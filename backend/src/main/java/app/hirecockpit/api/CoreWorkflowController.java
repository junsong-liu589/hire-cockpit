package app.hirecockpit.api;

import app.hirecockpit.security.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class CoreWorkflowController {
    private final JdbcTemplate db;
    public CoreWorkflowController(JdbcTemplate db) { this.db=db; }

    @GetMapping("/companies") public List<Map<String,Object>> companies(HttpServletRequest r, @RequestParam(defaultValue="") String q) {
        String w=workspace(r); String like="%"+q.trim()+"%";
        return db.queryForList("SELECT id,name,short_name AS shortName,group_name AS groupName,nature,industry,region,website,recruitment_website AS recruitmentWebsite,description,notes,created_at AS createdAt,updated_at AS updatedAt FROM company WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL AND (?='' OR name LIKE ? OR short_name LIKE ?) ORDER BY updated_at DESC LIMIT 500",w,q,like,like);
    }
    @GetMapping("/companies/{id}") public Map<String,Object> company(HttpServletRequest r,@PathVariable String id) {
        return one("SELECT id,name,short_name AS shortName,group_name AS groupName,nature,industry,region,website,recruitment_website AS recruitmentWebsite,description,notes FROM company WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",id,workspace(r));
    }
    @PostMapping("/companies") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createCompany(HttpServletRequest r,@Valid @RequestBody CompanyInput in) {
        String w=workspace(r), id=UUID.randomUUID().toString();
        db.update("INSERT INTO company(id,workspace_id,name,short_name,group_name,nature,industry,region,website,recruitment_website,description,notes) VALUES (?,UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?)",id,w,in.name(),in.shortName(),in.groupName(),in.nature(),in.industry(),in.region(),in.website(),in.recruitmentWebsite(),in.description(),in.notes());
        return Map.of("id",id,"name",in.name());
    }
    @PutMapping("/companies/{id}") @Transactional public Map<String,Object> updateCompany(HttpServletRequest r,@PathVariable String id,@Valid @RequestBody CompanyInput in) {
        String w=workspace(r); int n=db.update("UPDATE company SET name=?,short_name=?,group_name=?,nature=?,industry=?,region=?,website=?,recruitment_website=?,description=?,notes=? WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",in.name(),in.shortName(),in.groupName(),in.nature(),in.industry(),in.region(),in.website(),in.recruitmentWebsite(),in.description(),in.notes(),id,w);
        requireChanged(n); return Map.of("id",id,"name",in.name());
    }

    @GetMapping("/jobs") public List<Map<String,Object>> jobs(HttpServletRequest r,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Boolean favorite,@RequestParam(required=false) String companyId) {
        String w=workspace(r),like="%"+q.trim()+"%";
        return db.queryForList("SELECT j.id,j.company_id AS companyId,c.name AS companyName,j.title,j.department,j.job_number AS jobNumber,j.recruitment_type AS recruitmentType,j.batch,j.city,j.degree_requirement AS degreeRequirement,j.major_requirement AS majorRequirement,j.skill_requirement AS skillRequirement,j.salary,j.deadline,j.source_url AS sourceUrl,j.notes,j.is_favorite AS favorite,j.priority,j.created_at AS createdAt,j.updated_at AS updatedAt FROM job j JOIN company c ON c.id=j.company_id AND c.workspace_id=j.workspace_id WHERE j.workspace_id=UUID_TO_BIN(?) AND j.deleted_at IS NULL AND c.deleted_at IS NULL AND (?='' OR j.title LIKE ? OR c.name LIKE ? OR j.job_number LIKE ?) AND (? IS NULL OR j.is_favorite=?) AND (? IS NULL OR j.company_id=?) ORDER BY j.deadline IS NULL,j.deadline,j.updated_at DESC LIMIT 500",w,q,like,like,like,favorite,favorite,companyId,companyId);
    }
    @GetMapping("/jobs/{id}") public Map<String,Object> job(HttpServletRequest r,@PathVariable String id) {
        return one("SELECT j.id,j.company_id AS companyId,c.name AS companyName,j.title,j.department,j.job_number AS jobNumber,j.recruitment_type AS recruitmentType,j.batch,j.city,j.degree_requirement AS degreeRequirement,j.major_requirement AS majorRequirement,j.skill_requirement AS skillRequirement,j.salary,j.deadline,j.source_url AS sourceUrl,j.original_text AS originalText,j.notes,j.is_favorite AS favorite,j.priority FROM job j JOIN company c ON c.id=j.company_id AND c.workspace_id=j.workspace_id WHERE j.id=? AND j.workspace_id=UUID_TO_BIN(?) AND j.deleted_at IS NULL",id,workspace(r));
    }
    @PostMapping("/jobs") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createJob(HttpServletRequest r,@Valid @RequestBody JobInput in) {
        String w=workspace(r),id=UUID.randomUUID().toString(); requireCompany(w,in.companyId());
        db.update("INSERT INTO job(id,workspace_id,company_id,title,department,job_number,recruitment_type,batch,city,degree_requirement,major_requirement,skill_requirement,salary,deadline,source_url,original_text,notes,is_favorite,priority) VALUES (?,UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,w,in.companyId(),in.title(),in.department(),in.jobNumber(),in.recruitmentType(),in.batch(),in.city(),in.degreeRequirement(),in.majorRequirement(),in.skillRequirement(),in.salary(),in.deadline(),in.sourceUrl(),in.originalText(),in.notes(),in.favorite(),priority(in.priority()));
        return Map.of("id",id,"title",in.title());
    }
    @PutMapping("/jobs/{id}") @Transactional public Map<String,Object> updateJob(HttpServletRequest r,@PathVariable String id,@Valid @RequestBody JobInput in) {
        String w=workspace(r); requireCompany(w,in.companyId());
        int n=db.update("UPDATE job SET company_id=?,title=?,department=?,job_number=?,recruitment_type=?,batch=?,city=?,degree_requirement=?,major_requirement=?,skill_requirement=?,salary=?,deadline=?,source_url=?,original_text=?,notes=?,is_favorite=?,priority=? WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",in.companyId(),in.title(),in.department(),in.jobNumber(),in.recruitmentType(),in.batch(),in.city(),in.degreeRequirement(),in.majorRequirement(),in.skillRequirement(),in.salary(),in.deadline(),in.sourceUrl(),in.originalText(),in.notes(),in.favorite(),priority(in.priority()),id,w);
        requireChanged(n); return Map.of("id",id,"title",in.title());
    }
    @PostMapping("/jobs/{id}/favorite") @Transactional public Map<String,Object> favorite(HttpServletRequest r,@PathVariable String id,@RequestBody FavoriteInput in) {
        int n=db.update("UPDATE job SET is_favorite=? WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",in.favorite(),id,workspace(r)); requireChanged(n); return Map.of("id",id,"favorite",in.favorite());
    }

    @GetMapping("/applications") public List<Map<String,Object>> applications(HttpServletRequest r) {
        return db.queryForList("SELECT a.id,a.job_id AS jobId,j.title AS jobTitle,c.name AS companyName,a.resume_version_id AS resumeVersionId,a.applied_at AS appliedAt,a.channel,a.platform,a.platform_account AS platformAccount,a.referrer,a.notes,a.current_status AS status,a.current_stage AS stage,a.created_at AS createdAt FROM application a JOIN job j ON j.id=a.job_id AND j.workspace_id=a.workspace_id JOIN company c ON c.id=j.company_id AND c.workspace_id=j.workspace_id WHERE a.workspace_id=UUID_TO_BIN(?) AND a.deleted_at IS NULL ORDER BY a.updated_at DESC LIMIT 500",workspace(r));
    }
    @GetMapping("/resumes") public List<Map<String,Object>> resumes(HttpServletRequest r) {
        return db.queryForList("SELECT id,name,direction,version,notes,created_at AS createdAt FROM resume_version WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL ORDER BY updated_at DESC",workspace(r));
    }
    @GetMapping("/dictionaries/{category}") public List<Map<String,Object>> dictionary(HttpServletRequest r,@PathVariable String category) {
        return db.queryForList("SELECT id,label,stable_stage AS stableStage,color FROM dictionary_item WHERE workspace_id=UUID_TO_BIN(?) AND category=? AND deleted_at IS NULL ORDER BY label",workspace(r),category);
    }
    @PostMapping("/dictionaries/{category}") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> addDictionary(HttpServletRequest r,@PathVariable String category,@Valid @RequestBody DictionaryInput in) {
        if(!List.of("company_nature","job_type","recruitment_type","application_status","task_type","specialty").contains(category)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown dictionary category");
        String id=UUID.randomUUID().toString(),w=workspace(r),stable=in.stableStage()==null?null:stage(in.stableStage());
        db.update("INSERT INTO dictionary_item(id,workspace_id,category,label,stable_stage,color) VALUES (?,UUID_TO_BIN(?),?,?,?,?)",id,w,category,in.label(),stable,in.color());return Map.of("id",id,"label",in.label());
    }
    @GetMapping("/tags") public List<Map<String,Object>> tags(HttpServletRequest r) {
        return db.queryForList("SELECT id,name,category,color FROM tag WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL ORDER BY name",workspace(r));
    }
    @PostMapping("/tags") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> addTag(HttpServletRequest r,@Valid @RequestBody TagInput in) {
        String id=UUID.randomUUID().toString();db.update("INSERT INTO tag(id,workspace_id,name,category,color) VALUES (?,UUID_TO_BIN(?),?,?,?)",id,workspace(r),in.name(),in.category()==null?"岗位":in.category(),in.color());return Map.of("id",id,"name",in.name());
    }
    @DeleteMapping("/tags/{id}") @Transactional public void deleteTag(HttpServletRequest r,@PathVariable String id) {
        int n=db.update("UPDATE tag SET deleted_at=UTC_TIMESTAMP(6) WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",id,workspace(r));requireChanged(n);
    }
    @PutMapping("/jobs/{id}/tags") @Transactional public Map<String,Object> setJobTags(HttpServletRequest r,@PathVariable String id,@RequestBody TagAssignment in) {
        String w=workspace(r);requireJob(w,id);List<String> ids=in.tagIds()==null?List.of():in.tagIds().stream().distinct().toList();
        for(String tagId:ids) if(db.queryForObject("SELECT COUNT(*) FROM tag WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,tagId,w)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tag not found");
        db.update("DELETE FROM job_tag WHERE job_id=? AND workspace_id=UUID_TO_BIN(?)",id,w);
        for(String tagId:ids)db.update("INSERT INTO job_tag(workspace_id,job_id,tag_id) VALUES(UUID_TO_BIN(?),?,?)",w,id,tagId);
        return Map.of("jobId",id,"tagIds",ids);
    }
    @PostMapping("/resumes") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createResume(HttpServletRequest r,@Valid @RequestBody ResumeInput in) {
        String w=workspace(r),id=UUID.randomUUID().toString(); db.update("INSERT INTO resume_version(id,workspace_id,name,direction,version,notes) VALUES (?,UUID_TO_BIN(?),?,?,?,?)",id,w,in.name(),in.direction(),in.version(),in.notes()); return Map.of("id",id,"name",in.name());
    }
    @GetMapping("/applications/{id}") public Map<String,Object> application(HttpServletRequest r,@PathVariable String id) {
        return one("SELECT a.id,a.job_id AS jobId,j.title AS jobTitle,c.name AS companyName,a.resume_version_id AS resumeVersionId,a.applied_at AS appliedAt,a.channel,a.platform,a.platform_account AS platformAccount,a.referrer,a.notes,a.current_status AS status,a.current_stage AS stage FROM application a JOIN job j ON j.id=a.job_id AND j.workspace_id=a.workspace_id JOIN company c ON c.id=j.company_id AND c.workspace_id=j.workspace_id WHERE a.id=? AND a.workspace_id=UUID_TO_BIN(?) AND a.deleted_at IS NULL",id,workspace(r));
    }
    @PostMapping("/applications") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createApplication(HttpServletRequest r,@Valid @RequestBody ApplicationInput in) {
        String w=workspace(r),id=UUID.randomUUID().toString();
        requireJob(w,in.jobId()); if(in.resumeVersionId()!=null) requireResume(w,in.resumeVersionId());
        String status=in.status()==null?"已投递":in.status(), stable=mappedStage(w,status);
        db.update("INSERT INTO application(id,workspace_id,job_id,resume_version_id,applied_at,channel,platform,platform_account,referrer,notes,current_status,current_stage) VALUES (?,UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?)",id,w,in.jobId(),in.resumeVersionId(),in.appliedAt(),in.channel(),in.platform(),in.platformAccount(),in.referrer(),in.notes(),status,stable);
        db.update("INSERT INTO application_status_history(id,workspace_id,application_id,status_snapshot,stable_stage,note) VALUES (?,UUID_TO_BIN(?),?,?,?,?)",UUID.randomUUID().toString(),w,id,status,stable,"创建投递");
        return Map.of("id",id,"status",status,"stage",stable);
    }
    @PutMapping("/applications/{id}/status") @Transactional public Map<String,Object> changeStatus(HttpServletRequest r,@PathVariable String id,@Valid @RequestBody StatusInput in) {
        String w=workspace(r),s=mappedStage(w,in.status()); int n=db.update("UPDATE application SET current_status=?,current_stage=? WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",in.status(),s,id,w); requireChanged(n);
        db.update("INSERT INTO application_status_history(id,workspace_id,application_id,status_snapshot,stable_stage,note) VALUES (?,UUID_TO_BIN(?),?,?,?,?)",UUID.randomUUID().toString(),w,id,in.status(),s,in.note());
        return Map.of("id",id,"status",in.status(),"stage",s);
    }
    @GetMapping("/applications/{id}/history") public List<Map<String,Object>> history(HttpServletRequest r,@PathVariable String id) {
        String w=workspace(r); requireApplication(w,id);
        return db.queryForList("SELECT id,status_snapshot AS status,stable_stage AS stage,note,changed_at AS changedAt FROM application_status_history WHERE application_id=? AND workspace_id=UUID_TO_BIN(?) ORDER BY changed_at",id,w);
    }

    private void requireCompany(String w,String id) { if(db.queryForObject("SELECT COUNT(*) FROM company WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,id,w)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found"); }
    private void requireJob(String w,String id) { if(db.queryForObject("SELECT COUNT(*) FROM job WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,id,w)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found"); }
    private void requireResume(String w,String id) { if(db.queryForObject("SELECT COUNT(*) FROM resume_version WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,id,w)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Resume not found"); }
    private Map<String,Object> one(String sql,String id,String workspace) { List<Map<String,Object>> rows=db.queryForList(sql,id,workspace); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"); return rows.get(0); }
    private void requireApplication(String w,String id) { if(db.queryForObject("SELECT COUNT(*) FROM application WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,id,w)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Application not found"); }
    private static String workspace(HttpServletRequest r) { Object w=r.getAttribute(WorkspaceContext.ATTRIBUTE); if(w==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); return w.toString(); }
    private static void requireChanged(int n) { if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"); }
    private static String priority(String p) { if(p==null||!List.of("S","A","B","C").contains(p)) return "B"; return p; }
    private static String stage(String p) { if(!List.of("TODO","FAVORITE","APPLICATION","SCREENING","EXAM","INTERVIEW","OFFER","CLOSED").contains(p)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown stable stage"); return p; }
    private String mappedStage(String w,String status) {
        List<String> mapped=db.query("SELECT stable_stage FROM dictionary_item WHERE workspace_id=UUID_TO_BIN(?) AND category='application_status' AND label=? AND deleted_at IS NULL",(rs,n)->rs.getString(1),w,status);
        if(mapped.isEmpty()||mapped.get(0)==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status has no stable stage mapping");return stage(mapped.get(0));
    }

    public record CompanyInput(@NotBlank String name,String shortName,String groupName,String nature,String industry,String region,String website,String recruitmentWebsite,String description,String notes) {}
    public record JobInput(@NotBlank String companyId,@NotBlank String title,String department,String jobNumber,String recruitmentType,String batch,String city,String degreeRequirement,String majorRequirement,String skillRequirement,String salary,LocalDate deadline,String sourceUrl,String originalText,String notes,boolean favorite,String priority) {}
    public record ApplicationInput(@NotBlank String jobId,String resumeVersionId,Instant appliedAt,String channel,String platform,String platformAccount,String referrer,String notes,String status,String stage) {}
    public record ResumeInput(@NotBlank String name,String direction,String version,String notes) {}
    public record DictionaryInput(@NotBlank String label,String stableStage,String color) {}
    public record TagInput(@NotBlank String name,String category,String color) {}
    public record TagAssignment(List<String> tagIds) {}
    public record StatusInput(@NotBlank String status,@NotBlank String stage,String note) {}
    public record FavoriteInput(boolean favorite) {}
}
