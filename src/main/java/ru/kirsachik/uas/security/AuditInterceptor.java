package ru.kirsachik.uas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import ru.kirsachik.uas.model.AppUser;
import ru.kirsachik.uas.service.AuditService;

@Component
public class AuditInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public AuditInterceptor(CurrentUserService currentUserService, AuditService auditService) {
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!path.startsWith("/api/") || path.startsWith("/api/auth/")) {
            return;
        }
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return;
        }
        if (path.matches("/api/drones/\\d+/telemetry")) {
            return;
        }

        AppUser user = currentUserService.findFromRequest(request).orElse(null);
        if (user == null) {
            return;
        }

        try {
            String actionType = describeAction(method, path);
            String details = ex == null ? "Выполнено" : "Ошибка: " + ex.getMessage();
            auditService.log(user, method, path, actionType, response.getStatus(), details, request.getRemoteAddr());
        } catch (Exception ignored) {
            // Журнал действий не должен останавливать основную бизнес-операцию.
        }
    }

    private String describeAction(String method, String path) {
        if (path.startsWith("/api/drones") && "POST".equals(method)) return "Создание или подключение БПЛА";
        if (path.startsWith("/api/drones") && "DELETE".equals(method)) return "Удаление БПЛА";
        if (path.startsWith("/api/sessions") && path.endsWith("/start")) return "Запуск миссии";
        if (path.startsWith("/api/sessions") && path.endsWith("/pause")) return "Пауза миссии";
        if (path.startsWith("/api/sessions") && path.endsWith("/stop")) return "Остановка миссии";
        if (path.startsWith("/api/sessions") && path.endsWith("/repeat")) return "Повтор маршрута";
        if (path.equals("/api/sessions") && "POST".equals(method)) return "Создание миссии";
        if (path.startsWith("/api/alerts")) return "Обработка оповещения";
        if (path.startsWith("/api/admin/users")) return "Администрирование пользователей";
        if (path.startsWith("/api/admin/actions")) return "Проверка действий пользователей";
        return method + " " + path;
    }
}
