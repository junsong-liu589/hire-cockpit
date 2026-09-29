package app.hirecockpit.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class SecurityConfig {
    @Bean FilterRegistrationBean<WorkspaceFilter> disableContainerRegistration(WorkspaceFilter filter) {
        FilterRegistrationBean<WorkspaceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, WorkspaceFilter workspaceFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(workspaceFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable()).build();
    }
}
