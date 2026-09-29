package app.hirecockpit;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.time.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        ZoneId zone=ZoneId.of("Asia/Shanghai"); Instant due=LocalDate.now(zone).plusDays(1).atTime(22,0).atZone(zone).toInstant();
        ResponseEntity<String> settings=http.exchange(url("/api/v1/settings/reminders"),HttpMethod.PUT,new HttpEntity<>("{\"days\":[1],\"timeZone\":\"Asia/Shanghai\"}",ah),String.class);
        assertThat(settings.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> task=http.exchange(url("/api/v1/tasks"),HttpMethod.POST,new HttpEntity<>("{\"title\":\"Timezone task\",\"priority\":\"A\",\"timeZone\":\"Asia/Shanghai\",\"dueAt\":\""+due+"\"}",ah),String.class);
        assertThat(task.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String from=URLEncoder.encode(Instant.now().minusSeconds(60).toString(),StandardCharsets.UTF_8),to=URLEncoder.encode(due.plusSeconds(3600).toString(),StandardCharsets.UTF_8);
        ResponseEntity<String> calendar=http.exchange(url("/api/v1/calendar?from="+from+"&to="+to),HttpMethod.GET,new HttpEntity<>(ah),String.class);
        assertThat(calendar.getBody()).contains("Timezone task");
        ResponseEntity<String> first=http.exchange(url("/api/v1/notifications"),HttpMethod.GET,new HttpEntity<>(ah),String.class);
        ResponseEntity<String> second=http.exchange(url("/api/v1/notifications"),HttpMethod.GET,new HttpEntity<>(ah),String.class);
        assertThat(first.getBody()).contains("Timezone task");assertThat(first.getBody()).isEqualTo(second.getBody());
        ResponseEntity<String> bTasks=http.exchange(url("/api/v1/tasks"),HttpMethod.GET,new HttpEntity<>(bh),String.class);
        assertThat(bTasks.getBody()).doesNotContain("Timezone task");
        ResponseEntity<String> spoof=http.exchange(url("/api/v1/jobs"),HttpMethod.GET,new HttpEntity<>(headers("hc_workspace=fake",bCsrf)),String.class);
        assertThat(spoof.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    private String url(String path){return "http://localhost:"+port+path;}
    private String cookies(ResponseEntity<?> response){List<String> all=response.getHeaders().get(HttpHeaders.SET_COOKIE);return all.stream().filter(s->s.startsWith("hc_workspace=")).map(s->s.substring(0,s.indexOf(';'))).findFirst().orElseThrow();}
    private String csrf(ResponseEntity<?> response){List<String> all=response.getHeaders().get(HttpHeaders.SET_COOKIE);return all.stream().filter(s->s.startsWith("hc_csrf=")).map(s->s.substring("hc_csrf=".length(),s.indexOf(';'))).findFirst().orElseThrow();}
    private HttpHeaders headers(String cookie,String csrf){HttpHeaders h=new HttpHeaders();h.add(HttpHeaders.COOKIE,cookie+"; hc_csrf="+csrf);h.add("X-CSRF-Token",csrf);h.add(HttpHeaders.ORIGIN,"http://localhost:"+port);h.setContentType(MediaType.APPLICATION_JSON);return h;}
}

