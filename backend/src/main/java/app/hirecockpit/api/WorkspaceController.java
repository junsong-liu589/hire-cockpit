package app.hirecockpit.api;

import app.hirecockpit.security.WorkspaceContext;
import app.hirecockpit.security.WorkspaceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final JdbcTemplate jdbc; private final WorkspaceFilter filter;
    public WorkspaceController(JdbcTemplate jdbc, WorkspaceFilter filter) { this.jdbc=jdbc; this.filter=filter; }
    @GetMapping("/current") @Transactional public Map<String,String> current(HttpServletRequest req, HttpServletResponse res) {
        String id=(String)req.getAttribute(WorkspaceContext.ATTRIBUTE);
        if(id==null) id=filter.createWorkspace(res); else filter.ensureCsrf(req,res);
        return Map.of("workspaceId",id,"privacy","仅此浏览器可继续使用","recovery","请定期导出备份；清除 Cookie 后无法恢复访问");
    }
}
