package com.dataops.dms.config;

import com.dataops.dms.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.annotation.Resource;

/**
 * Spring Security + JWT 安全配置
 * 权限粒度：控制到菜单级别，不控制操作权限。
 * 用户能否看到某菜单由前端动态菜单决定，后端仅做认证拦截。
 * 菜单管理 API 仅 admin 角色可访问。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Resource
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors()
            .and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                // ============ 公开接口 ============
                .antMatchers("/api/v1/auth/**").permitAll()
                .antMatchers("/api/v1/health").permitAll()
                .antMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()

                // ============ 菜单管理（仅admin） ============
                .antMatchers("/api/v1/menus/user-tree").authenticated()
                .antMatchers("/api/v1/menus/**").hasAuthority("menu:manage")

                // ============ 其他接口仅需认证（权限粒度由前端菜单控制） ============
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
