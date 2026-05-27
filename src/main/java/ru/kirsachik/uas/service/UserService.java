package ru.kirsachik.uas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kirsachik.uas.dto.LoginRequest;
import ru.kirsachik.uas.dto.UserRequest;
import ru.kirsachik.uas.dto.UserUpdateRequest;
import ru.kirsachik.uas.model.AppUser;
import ru.kirsachik.uas.model.UserRole;
import ru.kirsachik.uas.repository.AppUserRepository;
import ru.kirsachik.uas.security.PasswordUtil;
import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private final AppUserRepository userRepository;

    public UserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AppUser findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + id));
    }

    @Transactional
    public AppUser login(LoginRequest request) {
        AppUser user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Неверный логин или пароль"));
        if (!user.isActive()) {
            throw new IllegalStateException("Пользователь заблокирован администратором");
        }
        if (!PasswordUtil.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Неверный логин или пароль");
        }
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public AppUser create(UserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Логин уже используется: " + username);
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(PasswordUtil.hash(request.password()));
        user.setRole(request.role());
        user.setActive(request.active());
        return userRepository.save(user);
    }

    @Transactional
    public AppUser update(Long id, UserUpdateRequest request) {
        AppUser user = findById(id);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(PasswordUtil.hash(request.password()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public void ensureDefaultUsers() {
        ensureUser("admin", "Администратор системы", "admin123", UserRole.ADMIN);
        ensureUser("operator", "Оператор БПЛА", "operator123", UserRole.OPERATOR);
        ensureUser("observer", "Наблюдатель", "observer123", UserRole.OBSERVER);
    }

    private void ensureUser(String username, String fullName, String password, UserRole role) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setRole(role);
        user.setActive(true);
        userRepository.save(user);
    }
}
