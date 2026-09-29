package app.hirecockpit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class WorkspaceFilter extends OncePerRequestFilter {
    private static final String WORKSPACE_COOKIE = "hc_workspace";
    private static final String CSRF_COOKIE = "hc_csrf";
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final boolean secure;
    private final int maxAge;
    private final ConcurrentHashMap<String,long[]> creationWindows = new ConcurrentHashMap<>();

    public WorkspaceFilter(JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${app.cookie-secure:false}") boolean secure,
            @org.springframework.beans.factory.annotation.Value("${app.cookie-max-age-days:365}") int maxAge) {
        this.jdbc = jdbc; this.secure = secure; this.maxAge = Math.max(1, maxAge);
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        boolean current = "/api/v1/workspaces/current".equals(req.getRequestURI()) && "GET".equals(req.getMethod());
        String credential = cookie(req, WORKSPACE_COOKIE);
        if (credential == null && current) {
            if (!allowWorkspaceCreation(req.getRemoteAddr())) { res.setStatus(429); res.setHeader("Retry-After","60"); return; }
            chain.doFilter(req, res); return;
        }
        if (credential == null) { reject(res, "WORKSPACE_REQUIRED"); return; }
        if (!credential.matches("[A-Za-z0-9_-]{40,50}")) { reject(res, "INVALID_WORKSPACE"); return; }
        String hash = hash(credential);
        var matches = jdbc.query("SELECT BIN_TO_UUID(workspace_id) FROM workspace_credential WHERE credential_hash=UNHEX(?) AND (expires_at IS NULL OR expires_at>UTC_TIMESTAMP(6))", (rs, n) -> rs.getString(1), hash);
        if (matches.size() != 1) { reject(res, "INVALID_WORKSPACE"); return; }
        if (!isRead(req.getMethod()) && !validCsrfAndOrigin(req)) { res.setStatus(403); res.setContentType("application/problem+json"); res.getWriter().write("{\"title\":\"CSRF validation failed\",\"status\":403,\"code\":\"CSRF_REJECTED\"}"); return; }
        req.setAttribute(WorkspaceContext.ATTRIBUTE, matches.get(0));
        if (current) ensureCsrf(req,res);
        chain.doFilter(req, res);
    }

    public String createWorkspace(HttpServletResponse res) {
        byte[] secret = new byte[32]; random.nextBytes(secret);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String id = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO workspace(id) VALUES (UUID_TO_BIN(?))", id);
        jdbc.update("INSERT INTO workspace_credential(id,workspace_id,credential_hash,expires_at) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),UNHEX(?),DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? DAY))",
                java.util.UUID.randomUUID().toString(), id, hash(credential), maxAge);
        jdbc.update("INSERT INTO dictionary_item(id,workspace_id,category,label,stable_stage,color) SELECT UUID_TO_BIN(UUID()),UUID_TO_BIN(?),category,label,stable_stage,color FROM dictionary_seed", id);
        setCookie(res, WORKSPACE_COOKIE, credential, true, maxAge * 86400);
        byte[] token = new byte[32]; random.nextBytes(token);
        setCookie(res, CSRF_COOKIE, Base64.getUrlEncoder().withoutPadding().encodeToString(token), false, maxAge * 86400);
        return id;
    }

    public void ensureCsrf(HttpServletRequest req, HttpServletResponse res) {
        if (cookie(req, CSRF_COOKIE) == null) {
            byte[] token = new byte[32]; random.nextBytes(token);
            setCookie(res, CSRF_COOKIE, Base64.getUrlEncoder().withoutPadding().encodeToString(token), false, maxAge * 86400);
        }
    }

    private boolean validCsrfAndOrigin(HttpServletRequest req) {
        String cookie = cookie(req, CSRF_COOKIE), header = req.getHeader("X-CSRF-Token");
        if (cookie == null || header == null || !MessageDigest.isEqual(cookie.getBytes(StandardCharsets.US_ASCII), header.getBytes(StandardCharsets.US_ASCII))) return false;
        String origin = req.getHeader("Origin");
        if (origin == null) return false;
        try { return java.net.URI.create(origin).getAuthority().equalsIgnoreCase(req.getHeader("Host")) && (secure ? origin.startsWith("https://") : origin.startsWith("http://")); }
        catch (RuntimeException ex) { return false; }
    }
    private boolean allowWorkspaceCreation(String address) {
        long now=System.currentTimeMillis(); long[] window=creationWindows.computeIfAbsent(address,k->new long[]{now,0});
        synchronized(window) { if(now-window[0]>=60_000){window[0]=now;window[1]=0;} return ++window[1]<=10; }
    }
    private void setCookie(HttpServletResponse res, String name, String value, boolean httpOnly, int seconds) {
        res.addHeader("Set-Cookie", name+"="+value+"; Path=/; Max-Age="+seconds+"; SameSite=Lax"+(httpOnly?"; HttpOnly":"")+(secure?"; Secure":""));
    }
    private static String cookie(HttpServletRequest req, String name) {
        Cookie[] cs=req.getCookies(); if(cs==null)return null;
        for(Cookie c:cs)if(name.equals(c.getName()))return c.getValue(); return null;
    }
    private static boolean isRead(String method) { return "GET".equals(method)||"HEAD".equals(method)||"OPTIONS".equals(method); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static void reject(HttpServletResponse res, String code) throws IOException {
        res.setStatus(401); res.setContentType("application/problem+json");
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Workspace authorization failed\",\"status\":401,\"code\":\""+code+"\"}");
    }
}
