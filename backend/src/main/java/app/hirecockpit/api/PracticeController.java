package app.hirecockpit.api;

import app.hirecockpit.security.WorkspaceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class PracticeController {
    private final JdbcTemplate db;private final ObjectMapper json;
    public PracticeController(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    @GetMapping("/exams") public List<Map<String,Object>> exams(HttpServletRequest r){return db.queryForList("SELECT id,application_id AS applicationId,platform,starts_at AS startsAt,time_zone AS timeZone,exam_url AS examUrl,admission_url AS admissionUrl,score,result,reflection,notes,created_at AS createdAt FROM exam WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL ORDER BY starts_at DESC",workspace(r));}
    @PostMapping("/exams") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createExam(HttpServletRequest r,@Valid @RequestBody ExamInput in){String w=workspace(r),id=UUID.randomUUID().toString();validateApplication(w,in.applicationId());zone(in.timeZone());db.update("INSERT INTO exam(id,workspace_id,application_id,platform,starts_at,time_zone,exam_url,admission_url,score,result,reflection,notes) VALUES (?,UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?)",id,w,in.applicationId(),in.platform(),ts(in.startsAt()),in.timeZone(),in.examUrl(),in.admissionUrl(),in.score(),in.result(),in.reflection(),in.notes());return Map.of("id",id,"platform",Objects.toString(in.platform(),"笔试"));}
    @DeleteMapping("/exams/{id}") @Transactional public void deleteExam(HttpServletRequest r,@PathVariable String id){softDelete("exam",id,workspace(r));}
    @GetMapping("/interviews") public List<Map<String,Object>> interviews(HttpServletRequest r){return db.queryForList("SELECT i.id,i.application_id AS applicationId,a.current_status AS applicationStatus,j.title AS jobTitle,c.name AS companyName,i.round_name AS roundName,i.starts_at AS startsAt,i.time_zone AS timeZone,i.modality,i.location,i.interviewer,i.question_notes AS questionNotes,i.rating,i.result,i.review,i.notes FROM interview i JOIN application a ON a.id=i.application_id AND a.workspace_id=i.workspace_id JOIN job j ON j.id=a.job_id AND j.workspace_id=a.workspace_id JOIN company c ON c.id=j.company_id AND c.workspace_id=j.workspace_id WHERE i.workspace_id=UUID_TO_BIN(?) AND i.deleted_at IS NULL ORDER BY i.starts_at DESC",workspace(r));}
    @PostMapping("/interviews") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createInterview(HttpServletRequest r,@Valid @RequestBody InterviewInput in){String w=workspace(r),id=UUID.randomUUID().toString();validateApplication(w,in.applicationId());zone(in.timeZone());if(in.rating()!=null&&(in.rating()<1||in.rating()>10))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Interview rating must be from 1 to 10");db.update("INSERT INTO interview(id,workspace_id,application_id,round_name,starts_at,time_zone,modality,location,interviewer,question_notes,rating,result,review,notes) VALUES (?,UUID_TO_BIN(?),?,?,?,?,?,?,?,CAST(? AS JSON),?,?,?,?)",id,w,in.applicationId(),in.roundName(),ts(in.startsAt()),in.timeZone(),in.modality(),in.location(),in.interviewer(),write(in.questionNotes()),in.rating(),in.result(),in.review(),in.notes());return Map.of("id",id,"roundName",in.roundName());}
    @DeleteMapping("/interviews/{id}") @Transactional public void deleteInterview(HttpServletRequest r,@PathVariable String id){softDelete("interview",id,workspace(r));}
    @GetMapping("/experience-notes") public List<Map<String,Object>> experienceNotes(HttpServletRequest r){return db.queryForList("SELECT e.id,e.company_id AS companyId,c.name AS companyName,e.job_id AS jobId,j.title AS jobTitle,e.round_name AS roundName,e.title,e.questions,e.answers,e.tags,e.source,e.used_count AS usedCount,e.created_at AS createdAt FROM experience_note e LEFT JOIN company c ON c.id=e.company_id AND c.workspace_id=e.workspace_id LEFT JOIN job j ON j.id=e.job_id AND j.workspace_id=e.workspace_id WHERE e.workspace_id=UUID_TO_BIN(?) AND e.deleted_at IS NULL ORDER BY e.updated_at DESC",workspace(r));}
    @PostMapping("/experience-notes") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createExperienceNote(HttpServletRequest r,@Valid @RequestBody ExperienceInput in){String w=workspace(r),id=UUID.randomUUID().toString();validateCompany(w,in.companyId());validateJob(w,in.jobId());db.update("INSERT INTO experience_note(id,workspace_id,company_id,job_id,round_name,title,questions,answers,tags,source) VALUES (?,UUID_TO_BIN(?),?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON),?)",id,w,in.companyId(),in.jobId(),in.roundName(),in.title(),write(in.questions()),write(in.answers()),write(in.tags()),in.source());return Map.of("id",id,"title",in.title());}
    @DeleteMapping("/experience-notes/{id}") @Transactional public void deleteExperienceNote(HttpServletRequest r,@PathVariable String id){softDelete("experience_note",id,workspace(r));}
    private void validateApplication(String w,String id){if(id!=null&&db.queryForObject("SELECT COUNT(*) FROM application WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,id,w)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Application not found");}
    private void validateCompany(String w,String id){if(id!=null&&db.queryForObject("SELECT COUNT(*) FROM company WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,id,w)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found");}
    private void validateJob(String w,String id){if(id!=null&&db.queryForObject("SELECT COUNT(*) FROM job WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,id,w)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found");}
    private void softDelete(String table,String id,String w){if(!List.of("exam","interview","experience_note").contains(table))throw new IllegalArgumentException();int n=db.update("UPDATE "+table+" SET deleted_at=UTC_TIMESTAMP(6) WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",id,w);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");}
    private String write(Object o){try{return json.writeValueAsString(o==null?List.of():o);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}
    private static Timestamp ts(Instant i){return i==null?null:Timestamp.from(i);}
    private static void zone(String z){try{if(z!=null)java.time.ZoneId.of(z);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid IANA time zone");}}
    private static String workspace(HttpServletRequest r){Object w=r.getAttribute(WorkspaceContext.ATTRIBUTE);if(w==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return w.toString();}
    public record ExamInput(String applicationId,String platform,Instant startsAt,String timeZone,String examUrl,String admissionUrl,Double score,String result,String reflection,String notes){}
    public record InterviewInput(@NotBlank String applicationId,@NotBlank String roundName,Instant startsAt,String timeZone,String modality,String location,String interviewer,List<Map<String,String>> questionNotes,Integer rating,String result,String review,String notes){}
    public record ExperienceInput(String companyId,String jobId,String roundName,@NotBlank String title,List<String> questions,List<String> answers,List<String> tags,String source){}
}
