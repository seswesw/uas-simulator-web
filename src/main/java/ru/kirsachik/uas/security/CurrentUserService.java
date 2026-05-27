package ru.kirsachik.uas.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import ru.kirsachik.uas.model.AppUser;
import ru.kirsachik.uas.model.UserRole;
import ru.kirsachik.uas.repository.AppUserRepository;
import java.util.Optional;

@Service
public class CurrentUserService {

    public static final String HEADER_USER_ID = "X-User-Id";

    private final AppUserRepository userRepository;

    public CurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUser> findFromRequest(HttpServletRequest request) {
        String rawId = request.getHeader(HEADER_USER_ID);
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        try {
            Long id = Long.valueOf(rawId);
            return userRepository.findById(id).filter(AppUser::isActive);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public AppUser requireUser(HttpServletRequest request) {
        return findFromRequest(request)
                .orElseThrow(() -> new SecurityException("Необходимо войти в систему"));
    }

    public AppUser requireAdmin(HttpServletRequest request) {
        AppUser user = requireUser(request);
        if (user.getRole() != UserRole.ADMIN) {
            throw new SecurityException("Доступ разрешён только администратору");
        }
        return user;
    }

    public boolean canModify(AppUser user) {
        return user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.OPERATOR;
    }
}
