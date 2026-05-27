package ru.kirsachik.uas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kirsachik.uas.dto.SessionRequest;
import ru.kirsachik.uas.dto.WaypointRequest;
import ru.kirsachik.uas.model.AlertLevel;
import ru.kirsachik.uas.model.Drone;
import ru.kirsachik.uas.model.DroneSourceType;
import ru.kirsachik.uas.model.DroneStatus;
import ru.kirsachik.uas.model.SessionStatus;
import ru.kirsachik.uas.model.SimulationSession;
import ru.kirsachik.uas.model.Waypoint;
import ru.kirsachik.uas.repository.SimulationSessionRepository;
import java.time.Instant;
import java.util.List;

@Service
public class SessionService {

    private final SimulationSessionRepository sessionRepository;
    private final DroneService droneService;
    private final SimulatorEngine simulatorEngine;
    private final AlertService alertService;

    public SessionService(
            SimulationSessionRepository sessionRepository,
            DroneService droneService,
            SimulatorEngine simulatorEngine,
            AlertService alertService) {
        this.sessionRepository = sessionRepository;
        this.droneService = droneService;
        this.simulatorEngine = simulatorEngine;
        this.alertService = alertService;
    }

    @Transactional(readOnly = true)
    public List<SimulationSession> findAll() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public SimulationSession findById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена: " + id));
    }

    @Transactional
    public SimulationSession create(SessionRequest request) {
        Drone drone = droneService.findById(request.droneId());
        if (drone.getSourceType() == DroneSourceType.EXTERNAL) {
            throw new IllegalStateException(
                    "Реальный БПЛА не участвует в симуляции. Используйте мониторинг и API телеметрии.");
        }
        if (drone.getStatus() == DroneStatus.FLYING || drone.getStatus() == DroneStatus.PAUSED) {
            throw new IllegalStateException("БПЛА уже участвует в полёте");
        }
        sessionRepository.findActiveSession().ifPresent(s -> {
            throw new IllegalStateException("Уже выполняется сессия #" + s.getId());
        });

        SimulationSession session = new SimulationSession();
        session.setName(request.name());
        session.setDescription(request.description());
        session.setDrone(drone);
        session.setStatus(SessionStatus.CREATED);

        int order = 0;
        for (WaypointRequest wp : request.waypoints()) {
            Waypoint waypoint = new Waypoint();
            waypoint.setSequenceOrder(order++);
            waypoint.setLatitude(wp.latitude());
            waypoint.setLongitude(wp.longitude());
            waypoint.setAltitudeM(Math.min(wp.altitudeM(), drone.getMaxAltitudeM()));
            waypoint.setSpeedMs(Math.min(wp.speedMs(), drone.getMaxSpeedMs()));
            session.addWaypoint(waypoint);
        }

        drone.setStatus(DroneStatus.READY);
        return sessionRepository.save(session);
    }

    @Transactional
    public SimulationSession repeat(Long sourceSessionId) {
        SimulationSession source = findById(sourceSessionId);
        if (source.getWaypoints() == null || source.getWaypoints().isEmpty()) {
            throw new IllegalStateException("У выбранной миссии нет маршрутных точек для повторения");
        }

        String sourceName = source.getName() == null ? "миссия" : source.getName();
        String copyName = "Повтор: " + sourceName;
        if (copyName.length() > 128) {
            copyName = copyName.substring(0, 128);
        }

        List<WaypointRequest> copiedWaypoints = source.getWaypoints().stream()
                .map(wp -> new WaypointRequest(
                        wp.getLatitude(),
                        wp.getLongitude(),
                        wp.getAltitudeM(),
                        wp.getSpeedMs()))
                .toList();

        String description = "Повтор маршрута из истории миссии #" + source.getId();
        return create(new SessionRequest(copyName, description, source.getDrone().getId(), copiedWaypoints));
    }

    @Transactional
    public SimulationSession start(Long id) {
        SimulationSession session = findById(id);
        if (session.getStatus() != SessionStatus.CREATED && session.getStatus() != SessionStatus.PAUSED) {
            throw new IllegalStateException("Нельзя запустить сессию в статусе " + session.getStatus());
        }
        session.setStatus(SessionStatus.RUNNING);
        if (session.getStartedAt() == null) {
            session.setStartedAt(Instant.now());
        }
        session.getDrone().setStatus(DroneStatus.FLYING);
        sessionRepository.save(session);
        simulatorEngine.start(session.getId());
        return session;
    }

    @Transactional
    public SimulationSession pause(Long id) {
        SimulationSession session = findById(id);
        if (session.getStatus() != SessionStatus.RUNNING) {
            throw new IllegalStateException("Сессия не выполняется");
        }
        session.setStatus(SessionStatus.PAUSED);
        session.getDrone().setStatus(DroneStatus.PAUSED);
        sessionRepository.save(session);
        simulatorEngine.pause(session.getId());
        simulatorEngine.publishSnapshot(session.getId());
        return session;
    }

    @Transactional
    public SimulationSession resume(Long id) {
        return start(id);
    }

    @Transactional
    public SimulationSession stop(Long id, boolean emergency) {
        SimulationSession session = findById(id);
        if (session.getStatus() != SessionStatus.RUNNING && session.getStatus() != SessionStatus.PAUSED) {
            throw new IllegalStateException("Остановить можно только выполняющуюся или приостановленную сессию");
        }
        session.setStatus(emergency ? SessionStatus.ABORTED : SessionStatus.COMPLETED);
        session.setFinishedAt(Instant.now());
        session.getDrone().setStatus(emergency ? DroneStatus.EMERGENCY : DroneStatus.IDLE);
        sessionRepository.save(session);
        simulatorEngine.publishSnapshot(session.getId());
        simulatorEngine.stop(session.getId());
        alertService.create(session.getId(), session.getDrone().getId(),
                emergency ? AlertLevel.CRITICAL : AlertLevel.INFO,
                emergency ? "Аварийная посадка выполнена" : "Симуляция остановлена");
        return session;
    }
}
