package ru.kirsachik.uas.service;

import org.springframework.stereotype.Service;
import ru.kirsachik.uas.dto.DashboardStats;
import ru.kirsachik.uas.model.DroneStatus;
import ru.kirsachik.uas.model.SessionStatus;
import ru.kirsachik.uas.repository.DroneRepository;
import ru.kirsachik.uas.repository.SimulationSessionRepository;
import java.util.List;

@Service
public class DashboardService {

    private final DroneRepository droneRepository;
    private final SimulationSessionRepository sessionRepository;
    private final AlertService alertService;

    public DashboardService(
            DroneRepository droneRepository,
            SimulationSessionRepository sessionRepository,
            AlertService alertService) {
        this.droneRepository = droneRepository;
        this.sessionRepository = sessionRepository;
        this.alertService = alertService;
    }

    public DashboardStats getStats() {
        int totalDrones = (int) droneRepository.count();
        int activeDrones = (int) droneRepository.countByStatusIn(
                List.of(DroneStatus.FLYING, DroneStatus.PAUSED));
        int totalSessions = (int) sessionRepository.count();
        int runningSessions = (int) sessionRepository.countByStatusIn(
                List.of(SessionStatus.RUNNING, SessionStatus.PAUSED));
        Long activeSessionId = sessionRepository.findActiveSession()
                .map(s -> s.getId())
                .orElse(null);
        return new DashboardStats(
                totalDrones,
                activeDrones,
                totalSessions,
                runningSessions,
                (int) alertService.countUnacknowledged(),
                activeSessionId
        );
    }
}
