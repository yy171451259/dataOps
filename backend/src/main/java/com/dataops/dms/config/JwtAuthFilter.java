package com.dataops.dms.config;

import com.dataops.dms.mapper.PermissionMapper;
import com.dataops.dms.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT认证过滤器
 * 验证Token后将用户权限写入Spring Security上下文
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private com.dataops.dms.mapper.UserMapper userMapper;

    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/health",
            "/swagger-ui",
            "/v3/api-docs",
            "/api/v1/health"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 跳过不需要认证的路径
        for (String excludePath : EXCLUDE_PATHS) {
            if (path.startsWith(excludePath)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 获取Token
        String token = extractToken(request);
        if (token == null) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\"}");
            return;
        }

        // 验证Token
        if (!jwtUtil.validateToken(token)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\"}");
            return;
        }

        // 获取用户信息
        String userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        // 查询用户权限并设置Spring Security上下文
        List<String> permissionCodes = permissionMapper.findPermissionCodesByUserId(userId);
        List<SimpleGrantedAuthority> authorities = permissionCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 将用户信息放入request attribute（兼容旧代码）
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        // 查询是否为管理员，放入 request attribute
        try {
            com.dataops.dms.entity.User user = userMapper.selectById(userId);
            if (user != null) {
                request.setAttribute("isAdmin", Boolean.TRUE.equals(user.getIsAdmin()));
            } else {
                request.setAttribute("isAdmin", false);
            }
        } catch (Exception e) {
            request.setAttribute("isAdmin", false);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
