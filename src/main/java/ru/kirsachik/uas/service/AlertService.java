package ru.kirsachik.uas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kirsachik.uas.model.Alert;
import ru.kirsachik.uas.model.AlertLevel;
import ru.kirsachik.uas.repository.AlertRepository;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional
    public Alert create(Long sessionId, Long droneId, AlertLevel level, String message) {
        Alert alert = new Alert();
        alert.setSessionId(sessionId);
        alert.setDroneId(droneId);
        alert.setLevel(level);
        alert.setMessage(message);
        return alertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public List<Alert> findRecent() {
        return alertRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional
    public Alert acknowledge(Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Оповещение не найдено: " + id));
        alert.setAcknowledged(true);
        return alertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public long countUnacknowledged() {
        return alertRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(a -> !a.isAcknowledged())
                .count();
    }
}
