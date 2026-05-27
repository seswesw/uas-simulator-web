package ru.kirsachik.uas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import ru.kirsachik.uas.model.AppUser;
import ru.kirsachik.uas.model.UserRole;
import java.io.IOException;
import java.util.Map;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public AuthorizationInterceptor(CurrentUserService currentUserService, ObjectMapper objectMapper) {
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!path.startsWith("/api/")) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
            return true;
        }
        if (isExternalTelemetry(path)) {
            return true;
        }

        AppUser user = currentUserService.findFromRequest(request).orElse(null);
        if (user == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Необходимо войти в систему");
            return false;
        }

        if (path.startsWith("/api/admin/") && user.getRole() != UserRole.ADMIN) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Доступ разрешён только администратору");
            return false;
        }

        if (!path.startsWith("/api/admin/") && !currentUserService.canModify(user)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Роль OBSERVER может только просматривать данные");
            return false;
        }

        return true;
    }

    private boolean isExternalTelemetry(String path) {
        return path.matches("/api/drones/\\d+/telemetry");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}
