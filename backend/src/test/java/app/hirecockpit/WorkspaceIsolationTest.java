package app.hirecockpit;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.time.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
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
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);r.add("app.encryption-key",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

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
        ResponseEntity<String> job=http.exchange(url("/api/v1/jobs"),HttpMethod.POST,new HttpEntity<>("{\"companyId\":\""+companyId+"\",\"title\":\"Backend role\"}",ah),String.class);
        String jobId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(job.getBody()).get("id").asText();
        ResponseEntity<String> application=http.exchange(url("/api/v1/applications"),HttpMethod.POST,new HttpEntity<>("{\"jobId\":\""+jobId+"\"}",ah),String.class);
        String applicationId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(application.getBody()).get("id").asText();
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
        Instant interviewAt=Instant.now().plusSeconds(90*60L);
        ResponseEntity<String> interview=http.exchange(url("/api/v1/interviews"),HttpMethod.POST,new HttpEntity<>("{\"applicationId\":\""+applicationId+"\",\"roundName\":\"一面\",\"startsAt\":\""+interviewAt+"\",\"timeZone\":\"Asia/Shanghai\",\"rating\":8}",ah),String.class);
        assertThat(interview.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> scheduled=http.exchange(url("/api/v1/calendar?from="+URLEncoder.encode(Instant.now().minusSeconds(60).toString(),StandardCharsets.UTF_8)+"&to="+URLEncoder.encode(interviewAt.plusSeconds(3600).toString(),StandardCharsets.UTF_8)),HttpMethod.GET,new HttpEntity<>(ah),String.class);
        assertThat(scheduled.getBody()).contains("一面");
        ResponseEntity<String> offer=http.exchange(url("/api/v1/offers"),HttpMethod.POST,new HttpEntity<>("{\"applicationId\":\""+applicationId+"\",\"baseSalary\":25000,\"bonus\":50000,\"workCity\":\"上海\",\"decision\":\"待决定\",\"evaluations\":{\"growth\":90,\"role\":85,\"location\":80,\"culture\":75}}",ah),String.class);
        assertThat(offer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> offerCompare=http.exchange(url("/api/v1/offers/comparison"),HttpMethod.GET,new HttpEntity<>(ah),String.class);
        assertThat(offerCompare.getBody()).contains("Private A","comparisonScore");
        ResponseEntity<String> bOffers=http.exchange(url("/api/v1/offers"),HttpMethod.GET,new HttpEntity<>(bh),String.class);
        assertThat(bOffers.getBody()).doesNotContain("Private A");
        ResponseEntity<String> crossOffer=http.exchange(url("/api/v1/offers"),HttpMethod.POST,new HttpEntity<>("{\"applicationId\":\""+applicationId+"\",\"baseSalary\":25000}",bh),String.class);
        assertThat(crossOffer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> analytics=http.exchange(url("/api/v1/analytics"),HttpMethod.GET,new HttpEntity<>(ah),String.class);
        assertThat(analytics.getBody()).contains("APPLICATION","OFFER","monthlyApplications");
        ResponseEntity<String> bTasks=http.exchange(url("/api/v1/tasks"),HttpMethod.GET,new HttpEntity<>(bh),String.class);
        assertThat(bTasks.getBody()).doesNotContain("Timezone task");
        String workspaceId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(aBootstrap.getBody()).get("workspaceId").asText();
        ResponseEntity<String> profile=http.exchange(url("/api/v1/profiles/personal"),HttpMethod.POST,new HttpEntity<>("{\"title\":\"基本信息\",\"fields\":{\"姓名\":\"敏感姓名\",\"手机号\":\"13800000000\"},\"visibleFields\":[\"姓名\"]}",ah),String.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> bProfile=http.exchange(url("/api/v1/profiles/personal"),HttpMethod.GET,new HttpEntity<>(bh),String.class);assertThat(bProfile.getBody()).doesNotContain("敏感姓名");
        byte[] encrypted=jdbc.queryForObject("SELECT encrypted_payload FROM profile_entry WHERE workspace_id=UUID_TO_BIN(?)",byte[].class,workspaceId);assertThat(new String(encrypted,StandardCharsets.UTF_8)).doesNotContain("敏感姓名");
        HttpHeaders multipartHeaders=headers(aCookie,aCsrf);multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String,Object> form=new LinkedMultiValueMap<>();form.add("file",new ByteArrayResource("%PDF-1.4\nprofile".getBytes(StandardCharsets.US_ASCII)){@Override public String getFilename(){return "../../resume.pdf";}});
        ResponseEntity<String> upload=http.exchange(url("/api/v1/files"),HttpMethod.POST,new HttpEntity<>(form,multipartHeaders),String.class);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);String fileId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(upload.getBody()).get("id").asText();
        LinkedMultiValueMap<String,Object> invalidForm=new LinkedMultiValueMap<>();invalidForm.add("file",new ByteArrayResource("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)){@Override public String getFilename(){return "malicious.pdf";}});
        ResponseEntity<String> rejectedUpload=http.exchange(url("/api/v1/files"),HttpMethod.POST,new HttpEntity<>(invalidForm,multipartHeaders),String.class);assertThat(rejectedUpload.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        ResponseEntity<String> privateDownload=http.exchange(url("/api/v1/files/"+fileId+"/download"),HttpMethod.GET,new HttpEntity<>(bh),String.class);
        assertThat(privateDownload.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<byte[]> ownDownload=http.exchange(url("/api/v1/files/"+fileId+"/download"),HttpMethod.GET,new HttpEntity<>(ah),byte[].class);
        assertThat(ownDownload.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(ownDownload.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("resume.pdf");
        ResponseEntity<byte[]> backup=http.exchange(url("/api/v1/backup/export"),HttpMethod.GET,new HttpEntity<>(ah),byte[].class);assertThat(backup.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(backup.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/zip"));
        HttpHeaders backupHeaders=headers(aCookie,aCsrf);backupHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);LinkedMultiValueMap<String,Object> previewForm=new LinkedMultiValueMap<>();previewForm.add("file",new ByteArrayResource(backup.getBody()){@Override public String getFilename(){return "backup.zip";}});
        ResponseEntity<String> preview=http.exchange(url("/api/v1/backup/preview"),HttpMethod.POST,new HttpEntity<>(previewForm,backupHeaders),String.class);assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(preview.getBody()).contains("sha256Verified","totalRows");
        ResponseEntity<String> restored=http.exchange(url("/api/v1/backup/restore"),HttpMethod.POST,new HttpEntity<>(previewForm,backupHeaders),String.class);assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange(url("/api/v1/companies"),HttpMethod.GET,new HttpEntity<>(ah),String.class).getBody()).contains("Private A");
        ResponseEntity<String> spoof=http.exchange(url("/api/v1/jobs"),HttpMethod.GET,new HttpEntity<>(headers("hc_workspace=fake",bCsrf)),String.class);
        assertThat(spoof.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    private String url(String path){return "http://localhost:"+port+path;}
    private String cookies(ResponseEntity<?> response){List<String> all=response.getHeaders().get(HttpHeaders.SET_COOKIE);return all.stream().filter(s->s.startsWith("hc_workspace=")).map(s->s.substring(0,s.indexOf(';'))).findFirst().orElseThrow();}
    private String csrf(ResponseEntity<?> response){List<String> all=response.getHeaders().get(HttpHeaders.SET_COOKIE);return all.stream().filter(s->s.startsWith("hc_csrf=")).map(s->s.substring("hc_csrf=".length(),s.indexOf(';'))).findFirst().orElseThrow();}
    private HttpHeaders headers(String cookie,String csrf){HttpHeaders h=new HttpHeaders();h.add(HttpHeaders.COOKIE,cookie+"; hc_csrf="+csrf);h.add("X-CSRF-Token",csrf);h.add(HttpHeaders.ORIGIN,"http://localhost:"+port);h.setContentType(MediaType.APPLICATION_JSON);return h;}
}

