package app.hirecockpit.api;

import app.hirecockpit.security.EncryptedPayloadService;
import app.hirecockpit.security.WorkspaceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {
    private static final Set<String> SECTIONS=Set.of("personal","contact","education","project","research","internship","skill","certificate","award","language");
    private final JdbcTemplate db;private final EncryptedPayloadService encryption;private final ObjectMapper json;
    public ProfileController(JdbcTemplate db,EncryptedPayloadService encryption,ObjectMapper json){this.db=db;this.encryption=encryption;this.json=json;}
    @GetMapping("/{section}") public List<Map<String,Object>> list(HttpServletRequest r,@PathVariable String section){checkSection(section);String w=workspace(r);return db.query("SELECT id,encrypted_payload,visible_fields,created_at,updated_at FROM profile_entry WHERE workspace_id=UUID_TO_BIN(?) AND section_key=? AND deleted_at IS NULL ORDER BY updated_at DESC",(rs,n)->view(rs.getString("id"),rs.getBytes("encrypted_payload"),rs.getString("visible_fields"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),w),w,section);}
    @PostMapping("/{section}") @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> create(HttpServletRequest r,@PathVariable String section,@Valid @RequestBody ProfileInput in){checkSection(section);String w=workspace(r),id=UUID.randomUUID().toString();List<String> visible=visible(in);db.update("INSERT INTO profile_entry(id,workspace_id,section_key,title,encrypted_payload,visible_fields) VALUES (?,UUID_TO_BIN(?),?,'entry',?,CAST(? AS JSON))",id,w,section,encryption.encrypt(w,new Payload(in.title(),in.fields())),write(visible));return Map.of("id",id,"section",section,"title",in.title(),"fields",in.fields(),"visibleFields",visible);}
    @PutMapping("/{section}/{id}") @Transactional public Map<String,Object> update(HttpServletRequest r,@PathVariable String section,@PathVariable String id,@Valid @RequestBody ProfileInput in){checkSection(section);String w=workspace(r);List<String> visible=visible(in);int n=db.update("UPDATE profile_entry SET encrypted_payload=?,visible_fields=CAST(? AS JSON) WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND section_key=? AND deleted_at IS NULL",encryption.encrypt(w,new Payload(in.title(),in.fields())),write(visible),id,w,section);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile entry not found");return Map.of("id",id,"section",section,"title",in.title(),"fields",in.fields(),"visibleFields",visible);}
    @DeleteMapping("/{section}/{id}") @Transactional public void delete(HttpServletRequest r,@PathVariable String section,@PathVariable String id){checkSection(section);int n=db.update("UPDATE profile_entry SET deleted_at=UTC_TIMESTAMP(6) WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND section_key=? AND deleted_at IS NULL",id,workspace(r),section);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile entry not found");}
    private Map<String,Object> view(String id,byte[] payload,String visibleFields,java.time.Instant created,java.time.Instant updated,String w){try{Payload p=encryption.decrypt(payload,Payload.class,w);return Map.of("id",id,"title",p.title(),"fields",p.fields(),"visibleFields",json.readValue(visibleFields,json.getTypeFactory().constructCollectionType(List.class,String.class)),"createdAt",created,"updatedAt",updated);}catch(Exception e){if(e instanceof ResponseStatusException x)throw x;throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Profile record could not be read",e);}}
    private static List<String> visible(ProfileInput in){if(in.fields()==null||in.fields().isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Profile fields are required");List<String> v=in.visibleFields()==null?new ArrayList<>(in.fields().keySet()):in.visibleFields().stream().distinct().toList();if(!in.fields().keySet().containsAll(v))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Visible fields must exist in the profile record");return v;}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}
    private static void checkSection(String section){if(!SECTIONS.contains(section))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown profile section");}
    private static String workspace(HttpServletRequest r){Object w=r.getAttribute(WorkspaceContext.ATTRIBUTE);if(w==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return w.toString();}
    public record ProfileInput(@NotBlank String title,Map<String,Object> fields,List<String> visibleFields){}
    public record Payload(String title,Map<String,Object> fields){}
}
