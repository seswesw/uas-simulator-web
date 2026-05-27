package ru.kirsachik.uas.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.kirsachik.uas.dto.ActionReviewRequest;
import ru.kirsachik.uas.dto.UserRequest;
import ru.kirsachik.uas.dto.UserUpdateRequest;
import ru.kirsachik.uas.model.AppUser;
import ru.kirsachik.uas.model.UserAction;
import ru.kirsachik.uas.security.CurrentUserService;
import ru.kirsachik.uas.service.AuditService;
import ru.kirsachik.uas.service.UserService;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public AdminController(UserService userService, AuditService auditService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/users")
    public List<AppUser> users(HttpServletRequest request) {
        currentUserService.requireAdmin(request);
        return userService.findAll();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AppUser createUser(@Valid @RequestBody UserRequest request, HttpServletRequest httpRequest) {
        currentUserService.requireAdmin(httpRequest);
        return userService.create(request);
    }

    @PutMapping("/users/{id}")
    public AppUser updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request, HttpServletRequest httpRequest) {
        currentUserService.requireAdmin(httpRequest);
        return userService.update(id, request);
    }

    @GetMapping("/actions")
    public List<UserAction> actions(@RequestParam(defaultValue = "100") int limit, HttpServletRequest request) {
        currentUserService.requireAdmin(request);
        return auditService.findRecent(limit);
    }

    @PutMapping("/actions/{id}/review")
    public UserAction reviewAction(@PathVariable Long id, @Valid @RequestBody ActionReviewRequest request, HttpServletRequest httpRequest) {
        currentUserService.requireAdmin(httpRequest);
        return auditService.review(id, request);
    }
}
