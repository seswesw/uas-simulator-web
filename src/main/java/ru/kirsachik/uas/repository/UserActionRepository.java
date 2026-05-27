package ru.kirsachik.uas.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.kirsachik.uas.model.UserAction;
import java.util.List;

public interface UserActionRepository extends JpaRepository<UserAction, Long> {
    List<UserAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
