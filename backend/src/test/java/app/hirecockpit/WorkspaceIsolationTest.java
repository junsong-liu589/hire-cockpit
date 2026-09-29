package app.hirecockpit;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkspaceIsolationTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
    }
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    @Test void browserCredentialsAreIsolatedAndCrossWorkspaceAssociationsFail() throws Exception {
        HttpHeaders empty = new HttpHeaders();
        ResponseEntity<String> aBootstrap = http.exchange(url("/api/v1/workspaces/current"), HttpMethod.GET, new HttpEntity<>(empty), String.class);
        ResponseEntity<String> bBootstrap = http.exchange(url("/api/v1/workspaces/current"), HttpMethod.GET, new HttpEntity<>(empty), String.class);
        String aCookie = cookies(aBootstrap), bCookie = cookies(bBootstrap);
        assertThat(aCookie).isNotEqualTo(bCookie);
        String aCsrf = csrf(aBootstrap), bCsrf = csrf(bBootstrap);
        HttpHeaders ah = headers(aCookie,aCsrf), bh=headers(bCookie,bCsrf);
        ResponseEntity<String> company = http.exchange(url("/api/v1/companies"),HttpMethod.POST,new HttpEntity<>("{\"name\":\"Private A\"}",ah),String.class);
        assertThat(company.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String companyId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(company.getBody()).get("id").asText();
        ResponseEntity<String> bCompanies=http.exchange(url("/api/v1/companies"),HttpMethod.GET,new HttpEntity<>(bh),String.class);
        assertThat(bCompanies.getBody()).doesNotContain("Private A");
        ResponseEntity<String> cross=http.exchange(url("/api/v1/jobs"),HttpMethod.POST,new HttpEntity<>("{\"companyId\":\""+companyId+"\",\"title\":\"Should not exist\"}",bh),String.class);
        assertThat(cross.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> spoof=http.exchange(url("/api/v1/jobs"),HttpMethod.GET,new HttpEntity<>(headers("hc_workspace=fake",bCsrf)),String.class);
        assertThat(spoof.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    private String url(String path){return "http://localhost:"+port+path;}
    private String cookies(ResponseEntity<?> response){List<String> all=response.getHeaders().get(HttpHeaders.SET_COOKIE);return all.stream().filter(s->s.startsWith("hc_workspace=")).map(s->s.substring(0,s.indexOf(';'))).findFirst().orElseThrow();}
    private String csrf(ResponseEntity<?> response){List<String> all=response.getHeaders().get(HttpHeaders.SET_COOKIE);return all.stream().filter(s->s.startsWith("hc_csrf=")).map(s->s.substring("hc_csrf=".length(),s.indexOf(';'))).findFirst().orElseThrow();}
    private HttpHeaders headers(String cookie,String csrf){HttpHeaders h=new HttpHeaders();h.add(HttpHeaders.COOKIE,cookie+"; hc_csrf="+csrf);h.add("X-CSRF-Token",csrf);h.add(HttpHeaders.ORIGIN,"http://localhost:"+port);h.setContentType(MediaType.APPLICATION_JSON);return h;}
}

