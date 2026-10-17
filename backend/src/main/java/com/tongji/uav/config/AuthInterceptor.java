package com.tongji.uav.config;

import com.tongji.uav.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器：校验 Authorization: Bearer &lt;token&gt;。
 * 通过后将 userId / username / role 放入 request attribute 供后续使用。
 * 角色权限：用户管理接口与非 GET 写操作仅 admin（规划 /api/plan 对所有登录用户开放）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检请求不携带自定义头，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "未登录或令牌缺失");
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(auth.substring(7));
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            request.setAttribute("username", claims.get("username", String.class));
            request.setAttribute("role", claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "令牌无效或已过期");
            return false;
        }
        // 角色权限：/api/users 全部接口与非 GET 写操作仅 admin；POST /api/plan 对所有登录用户开放
        String role = (String) request.getAttribute("role");
        String path = request.getRequestURI();
        boolean adminOnly = path.startsWith("/api/users")
                || (!HttpMethod.GET.matches(request.getMethod()) && !path.startsWith("/api/plan"));
        if (adminOnly && !"admin".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "需要管理员权限");
            return false;
        }
        return true;
    }
}
