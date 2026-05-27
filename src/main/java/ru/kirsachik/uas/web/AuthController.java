package ru.kirsachik.uas.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.kirsachik.uas.dto.LoginRequest;
import ru.kirsachik.uas.model.AppUser;
import ru.kirsachik.uas.security.CurrentUserService;
import ru.kirsachik.uas.service.AuditService;
import ru.kirsachik.uas.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public AuthController(UserService userService, CurrentUserService currentUserService, AuditService auditService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public AppUser login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AppUser user = userService.login(request);
        auditService.log(user, "POST", "/api/auth/login", "Вход в систему", 200, "Пользователь вошёл в систему", httpRequest.getRemoteAddr());
        return user;
    }

    @GetMapping("/me")
    public AppUser me(HttpServletRequest request) {
        return currentUserService.requireUser(request);
    }
}
