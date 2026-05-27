package ru.kirsachik.uas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kirsachik.uas.model.AppUser;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
