package app.hirecockpit.api;

import app.hirecockpit.security.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final JdbcTemplate db; private final Path root;
    public FileController(JdbcTemplate db,@Value("${app.upload-dir:./data/uploads}") String uploadDir){this.db=db;this.root=Path.of(uploadDir).toAbsolutePath().normalize();}

    @GetMapping public List<Map<String,Object>> list(HttpServletRequest r){return db.queryForList("SELECT id,original_name AS originalName,media_type AS mediaType,file_size AS size,sha256,created_at AS createdAt FROM stored_file WHERE workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 200",workspace(r));}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> upload(HttpServletRequest r,@RequestPart("file") MultipartFile file){
        if(file.isEmpty()||file.getSize()>20L*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"File is empty or exceeds the 20 MiB limit");
        String name=safeName(file.getOriginalFilename());byte[] bytes;try{bytes=file.getBytes();}catch(IOException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unable to read upload",e);}
        String ext=extension(name),media=magic(bytes,ext);if(media==null||!extensionMatches(ext,media))throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"File extension and validated content do not match a supported PDF, image, or office document");
        String w=workspace(r),id=UUID.randomUUID().toString(),hash=sha256(bytes);Path dir=workspaceDirectory(w),temp=null,target=dir.resolve(id).normalize();
        if(!target.startsWith(dir))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid file key");
        try{Files.createDirectories(dir);temp=Files.createTempFile(dir,".upload-",".tmp");Files.write(temp,bytes,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}catch(IOException e){if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Upload storage failed",e);}
        try{db.update("INSERT INTO stored_file(id,workspace_id,original_name,media_type,file_size,sha256) VALUES (?,UUID_TO_BIN(?),?,?,?,?)",id,w,name,media,bytes.length,hash);}catch(RuntimeException e){try{Files.deleteIfExists(target);}catch(IOException ignored){}throw e;}
        Arrays.fill(bytes,(byte)0);return Map.of("id",id,"originalName",name,"mediaType",media,"size",file.getSize(),"sha256",hash);
    }
    @GetMapping("/{id}/download") public ResponseEntity<Resource> download(HttpServletRequest r,@PathVariable String id){
        String w=workspace(r);List<Map<String,Object>> rows=db.queryForList("SELECT original_name,media_type,file_size FROM stored_file WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",id,w);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");
        Path target=workspaceDirectory(w).resolve(id).normalize();if(!target.startsWith(workspaceDirectory(w))||!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");
        Map<String,Object> f=rows.get(0);ContentDisposition disposition=ContentDisposition.attachment().filename((String)f.get("original_name"),java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)f.get("media_type"))).contentLength(((Number)f.get("file_size")).longValue()).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("Content-Security-Policy","default-src 'none'; sandbox").body(new FileSystemResource(target));
    }
    @DeleteMapping("/{id}") @Transactional public void delete(HttpServletRequest r,@PathVariable String id){String w=workspace(r);int n=db.update("UPDATE stored_file SET deleted_at=UTC_TIMESTAMP(6) WHERE id=? AND workspace_id=UUID_TO_BIN(?) AND deleted_at IS NULL",id,w);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");}

    private Path workspaceDirectory(String w){Path p=root.resolve(w).normalize();if(!p.startsWith(root))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid workspace path");return p;}
    private static String safeName(String raw){if(raw==null||raw.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filename is required");String n=raw.replace('\\','/');n=n.substring(n.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_").trim();if(n.isBlank()||n.equals(".")||n.equals(".."))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid filename");return n.length()>255?n.substring(n.length()-255):n;}
    private static String extension(String name){int i=name.lastIndexOf('.');return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT);}
    private static boolean extensionMatches(String ext,String mime){return ("pdf".equals(ext)&&MediaType.APPLICATION_PDF_VALUE.equals(mime))||("png".equals(ext)&&MediaType.IMAGE_PNG_VALUE.equals(mime))||(("jpg".equals(ext)||"jpeg".equals(ext))&&MediaType.IMAGE_JPEG_VALUE.equals(mime))||("docx".equals(ext)&&"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime))||("xlsx".equals(ext)&&"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(mime))||("pptx".equals(ext)&&"application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(mime))||("doc".equals(ext)&&"application/msword".equals(mime))||("xls".equals(ext)&&"application/vnd.ms-excel".equals(mime))||("ppt".equals(ext)&&"application/vnd.ms-powerpoint".equals(mime));}
    private static String magic(byte[] b,String ext){if(b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-')return MediaType.APPLICATION_PDF_VALUE;if(b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return MediaType.IMAGE_PNG_VALUE;if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return MediaType.IMAGE_JPEG_VALUE;
        if(b.length>=8&&(b[0]&255)==208&&(b[1]&255)==207&&(b[2]&255)==17&&(b[3]&255)==224&&(b[4]&255)==161&&(b[5]&255)==177&&(b[6]&255)==26&&(b[7]&255)==225){if(ext.equals("doc"))return "application/msword";if(ext.equals("xls"))return "application/vnd.ms-excel";if(ext.equals("ppt"))return "application/vnd.ms-powerpoint";return null;}
        if(b.length>=4&&b[0]=='P'&&b[1]=='K'&&b[2]==3&&b[3]==4){String required=switch(ext){case "docx"->"word/document.xml";case "xlsx"->"xl/workbook.xml";case "pptx"->"ppt/presentation.xml";default->null;};if(required==null)return null;try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(b))){java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];long total=0;int count=0;boolean found=false;while((entry=zip.getNextEntry())!=null){if(++count>1000)return null;if(required.equals(entry.getName()))found=true;int read;while((read=zip.read(buffer))!=-1){total+=read;if(total>64L*1024*1024)return null;}zip.closeEntry();}if(!found)return null;}catch(IOException e){return null;}return switch(ext){case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";case "xlsx"->"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";case "pptx"->"application/vnd.openxmlformats-officedocument.presentationml.presentation";default->null;}}
        return null;}
    private static String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String workspace(HttpServletRequest r){Object w=r.getAttribute(WorkspaceContext.ATTRIBUTE);if(w==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return w.toString();}
}
