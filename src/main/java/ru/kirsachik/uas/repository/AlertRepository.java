package ru.kirsachik.uas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kirsachik.uas.model.Alert;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findTop50ByOrderByCreatedAtDesc();
}
