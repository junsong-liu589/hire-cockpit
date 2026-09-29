package app.hirecockpit.api;

import app.hirecockpit.security.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.sql.Timestamp;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class PlanningController {
    private final JdbcTemplate db;
    public PlanningController(JdbcTemplate db) { this.db=db; }

    @GetMapping("/tasks") public List<Map<String,Object>> tasks(HttpServletRequest r,@RequestParam(defaultValue="false") boolean includeCompleted) {
        String w=workspace(r); return db.queryForList("SELECT id,title,description,task_type AS taskType,priority,due_at AS dueAt,time_zone AS timeZone,job_id AS jobId,application_id AS applicationId,completed,completed_at AS completedAt,created_at AS createdAt FROM task WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL AND (?=TRUE OR completed=FALSE) ORDER BY completed,due_at IS NULL,due_at",w,includeCompleted);
    }
    @PostMapping("/tasks") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createTask(HttpServletRequest r,@Valid @RequestBody TaskInput in) {
        String w=workspace(r),id=UUID.randomUUID().toString(); validateLinks(w,in.jobId(),in.applicationId()); zone(in.timeZone());
        db.update("INSERT INTO task(id,workspace_id,title,description,task_type,priority,due_at,time_zone,job_id,application_id) VALUES (?,UUID_TO_BIN(?),?,?,?,?,?,?,?,?)",id,w,in.title(),in.description(),in.taskType()==null?"其他":in.taskType(),priority(in.priority()),ts(in.dueAt()),in.timeZone(),in.jobId(),in.applicationId());
        return Map.of("id",id,"title",in.title());
    }
    @PutMapping("/tasks/{id}") @Transactional public Map<String,Object> updateTask(HttpServletRequest r,@PathVariable String id,@Valid @RequestBody TaskInput in) {
        String w=workspace(r); validateLinks(w,in.jobId(),in.applicationId()); zone(in.timeZone());
        int n=db.update("UPDATE task SET title=?,description=?,task_type=?,priority=?,due_at=?,time_zone=?,job_id=?,application_id=? WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",in.title(),in.description(),in.taskType()==null?"其他":in.taskType(),priority(in.priority()),ts(in.dueAt()),in.timeZone(),in.jobId(),in.applicationId(),id,w);
        requireChanged(n);return Map.of("id",id,"title",in.title());
    }
    @PutMapping("/tasks/{id}/complete") @Transactional public Map<String,Object> complete(HttpServletRequest r,@PathVariable String id,@RequestBody CompleteInput in) {
        int n=db.update("UPDATE task SET completed=?,completed_at=IF(?,UTC_TIMESTAMP(6),NULL) WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",in.completed(),in.completed(),id,workspace(r));requireChanged(n);return Map.of("id",id,"completed",in.completed());
    }

    @GetMapping("/calendar") public List<Map<String,Object>> calendar(HttpServletRequest r,@RequestParam Instant from,@RequestParam Instant to) {
        if(!from.isBefore(to)||to.isAfter(from.plusSeconds(93L*86400))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Calendar range must be positive and at most 93 days");
        String w=workspace(r); List<Map<String,Object>> out=new ArrayList<>();
        out.addAll(db.queryForList("SELECT id,title,event_type AS eventType,starts_at AS startsAt,ends_at AS endsAt,time_zone AS timeZone,notes,job_id AS jobId,application_id AS applicationId,'EVENT' AS sourceType FROM calendar_event WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL AND starts_at>=? AND starts_at<?",w,ts(from),ts(to)));
        out.addAll(db.queryForList("SELECT id,title,task_type AS eventType,due_at AS startsAt,NULL AS endsAt,time_zone AS timeZone,description AS notes,job_id AS jobId,application_id AS applicationId,'TASK' AS sourceType FROM task WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL AND due_at>=? AND due_at<?",w,ts(from),ts(to)));
        ZoneId workspaceZone=zone(timeZone(w));LocalDate first=from.atZone(workspaceZone).toLocalDate(),last=to.atZone(workspaceZone).toLocalDate().plusDays(1);
        List<Map<String,Object>> deadlines=db.queryForList("SELECT j.id,j.title,j.deadline,j.notes FROM job j WHERE j.workspace_id=UUID_TO_BIN(?) AND j.deleted_at IS NULL AND j.deadline>=? AND j.deadline<?",w,first,last);
        for(Map<String,Object> job:deadlines){LocalDate deadline=((java.sql.Date)job.get("deadline")).toLocalDate();Instant starts=deadline.atTime(23,59,59).atZone(workspaceZone).toInstant();if(!starts.isBefore(from)&&starts.isBefore(to)){Map<String,Object> event=new HashMap<>(job);event.put("eventType","JOB_DEADLINE");event.put("startsAt",starts);event.put("timeZone",workspaceZone.getId());event.put("jobId",job.get("id"));event.put("sourceType","JOB");out.add(event);}}
        out.sort(Comparator.comparing(x->Objects.toString(x.get("startsAt"),"")));return out;
    }
    @PostMapping("/calendar/events") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> createEvent(HttpServletRequest r,@Valid @RequestBody EventInput in) {
        String w=workspace(r),id=UUID.randomUUID().toString();if(in.endsAt()!=null&&in.endsAt().isBefore(in.startsAt()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"End precedes start");zone(in.timeZone());validateLinks(w,in.jobId(),in.applicationId());
        db.update("INSERT INTO calendar_event(id,workspace_id,title,event_type,starts_at,ends_at,time_zone,notes,job_id,application_id) VALUES (?,UUID_TO_BIN(?),?,?,?,?,?,?,?,?)",id,w,in.title(),in.eventType()==null?"PERSONAL":in.eventType(),ts(in.startsAt()),ts(in.endsAt()),in.timeZone()==null?"UTC":in.timeZone(),in.notes(),in.jobId(),in.applicationId());return Map.of("id",id,"title",in.title());
    }

    @GetMapping("/settings/reminders") public Map<String,Object> reminderSettings(HttpServletRequest r) {
        String w=workspace(r);List<Map<String,Object>> rows=db.queryForList("SELECT setting_value FROM workspace_setting WHERE workspace_id=UUID_TO_BIN(?) AND setting_key='reminder.days'",w);
        List<Integer> days=rows.isEmpty()?List.of(7,3,1,0):parseDays(rows.get(0).get("setting_value"));return Map.of("days",days,"timeZone",timeZone(w));
    }
    @PutMapping("/settings/reminders") @Transactional public Map<String,Object> updateReminderSettings(HttpServletRequest r,@Valid @RequestBody ReminderSettingsInput in) {
        String w=workspace(r);List<Integer> days=in.days().stream().distinct().sorted(Comparator.reverseOrder()).toList();if(days.size()!=in.days().size()||days.stream().anyMatch(d->d<0||d>30))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reminder days must be unique values from 0 to 30");ZoneId z=zone(in.timeZone());
        String json=days.toString();db.update("INSERT INTO workspace_setting(workspace_id,setting_key,setting_value) VALUES(UUID_TO_BIN(?),'reminder.days',CAST(? AS JSON)) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)",w,json);
        db.update("INSERT INTO workspace_setting(workspace_id,setting_key,setting_value) VALUES(UUID_TO_BIN(?),'time.zone',JSON_QUOTE(?)) ON DUPLICATE KEY UPDATE setting_value=JSON_QUOTE(?)",w,z.getId(),z.getId());return Map.of("days",days,"timeZone",z.getId());
    }
    @GetMapping("/notifications") @Transactional public List<Map<String,Object>> notifications(HttpServletRequest r,@RequestParam(defaultValue="false") boolean unreadOnly) {
        String w=workspace(r);generateNotifications(w);return db.queryForList("SELECT id,source_type AS sourceType,source_id AS sourceId,reminder_key AS reminderKey,title,due_at AS dueAt,created_at AS createdAt,read_at AS readAt FROM notification_event WHERE workspace_id=UUID_TO_BIN(?) AND (?=FALSE OR read_at IS NULL) ORDER BY read_at IS NOT NULL,due_at LIMIT 200",w,unreadOnly);
    }
    @PutMapping("/notifications/{id}/read") @Transactional public Map<String,Object> readNotification(HttpServletRequest r,@PathVariable String id) {
        int n=db.update("UPDATE notification_event SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE id=? AND workspace_id=UUID_TO_BIN(?)",id,workspace(r));requireChanged(n);return Map.of("id",id,"read",true);
    }

    private void generateNotifications(String w) {
        List<Map<String,Object>> settings=db.queryForList("SELECT setting_value FROM workspace_setting WHERE workspace_id=UUID_TO_BIN(?) AND setting_key='reminder.days'",w);List<Integer> days=settings.isEmpty()?List.of(7,3,1,0):parseDays(settings.get(0).get("setting_value"));
        String workspaceZone=timeZone(w); ZoneId defaultZone=zone(workspaceZone);Instant now=Instant.now();
        List<DueItem> tasks=db.query("SELECT id,title,due_at,time_zone FROM task WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL AND completed=FALSE AND due_at>=? AND due_at<?",(rs,n)->new DueItem(rs.getString("id"),rs.getString("title"),rs.getTimestamp("due_at").toInstant(),rs.getString("time_zone"),"TASK"),w,ts(now),ts(now.plus(32,ChronoUnit.DAYS)));
        for(DueItem item:tasks) {
            ZoneId z=item.timeZone()==null?defaultZone:zone(item.timeZone()); long offset=ChronoUnit.DAYS.between(LocalDate.now(z),item.dueAt().atZone(z).toLocalDate());
            if(days.contains((int)offset)) insertNotification(w,item,"D-"+offset,"待办即将到期："+item.title());
        }
        List<DueItem> jobs=db.query("SELECT id,title,deadline FROM job WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL AND deadline>=? AND deadline<?",(rs,n)->new DueItem(rs.getString("id"),rs.getString("title"),rs.getDate("deadline").toLocalDate().atTime(23,59,59).atZone(defaultZone).toInstant(),workspaceZone,"JOB"),w,LocalDate.now(defaultZone),LocalDate.now(defaultZone).plusDays(32));
        for(DueItem item:jobs) {
            ZoneId z=defaultZone;long offset=ChronoUnit.DAYS.between(LocalDate.now(z),item.dueAt().atZone(z).toLocalDate());
            if(days.contains((int)offset)) insertNotification(w,item,"D-"+offset,"岗位截止："+item.title());
        }
    }
    private void insertNotification(String w,DueItem item,String reminderKey,String title){db.update("INSERT IGNORE INTO notification_event(id,workspace_id,source_type,source_id,reminder_key,title,due_at) VALUES(?,UUID_TO_BIN(?),?,?,?,?,?)",UUID.randomUUID().toString(),w,item.sourceType(),item.id(),reminderKey,title,ts(item.dueAt()));}
    private String timeZone(String w){List<String> rows=db.query("SELECT JSON_UNQUOTE(setting_value) FROM workspace_setting WHERE workspace_id=UUID_TO_BIN(?) AND setting_key='time.zone'",(rs,n)->rs.getString(1),w);return rows.isEmpty()?"UTC":rows.get(0);}
    private static List<Integer> parseDays(Object value){try{String s=value.toString();if(s.startsWith("\""))s=s.substring(1,s.length()-1);return Arrays.stream(s.replace("[","").replace("]","").split(",")).map(String::trim).filter(x->!x.isEmpty()).map(Integer::parseInt).toList();}catch(Exception e){return List.of(7,3,1,0);}}
    private void validateLinks(String w,String job,String application){if(job!=null&&db.queryForObject("SELECT COUNT(*) FROM job WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,job,w)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found");if(application!=null&&db.queryForObject("SELECT COUNT(*) FROM application WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",Integer.class,application,w)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Application not found");}
    private static String workspace(HttpServletRequest r){Object w=r.getAttribute(WorkspaceContext.ATTRIBUTE);if(w==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return w.toString();}
    private static String priority(String p){return p!=null&&List.of("S","A","B","C").contains(p)?p:"B";}
    private static ZoneId zone(String z){try{return ZoneId.of(z==null?"UTC":z);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid IANA time zone");}}
    private static void requireChanged(int n){if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");}
    private static Timestamp ts(Instant i){return i==null?null:Timestamp.from(i);}
    public record TaskInput(@NotBlank String title,String description,String taskType,String priority,Instant dueAt,String timeZone,String jobId,String applicationId){}
    public record CompleteInput(boolean completed){}
    public record EventInput(@NotBlank String title,String eventType,@jakarta.validation.constraints.NotNull Instant startsAt,Instant endsAt,String timeZone,String notes,String jobId,String applicationId){}
    public record ReminderSettingsInput(List<Integer> days,String timeZone){public ReminderSettingsInput{if(days==null)days=List.of(7,3,1,0);if(timeZone==null)timeZone="UTC";}}
    private record DueItem(String id,String title,Instant dueAt,String timeZone,String sourceType){}
}
