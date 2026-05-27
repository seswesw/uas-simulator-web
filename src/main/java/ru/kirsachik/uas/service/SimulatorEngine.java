package ru.kirsachik.uas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kirsachik.uas.dto.TelemetryMessage;
import ru.kirsachik.uas.model.*;
import ru.kirsachik.uas.repository.SimulationSessionRepository;
import ru.kirsachik.uas.repository.TelemetryRecordRepository;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulatorEngine {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double MIN_SEGMENT_DISTANCE_METERS = 0.5;
    private static final double MIN_SIMULATION_SPEED_MS = 0.1;
    private static final double MIN_ACCELERATION_MS2 = 1.0;
    private static final double SECONDS_TO_REACH_MAX_SPEED = 8.0;
    private static final double BATTERY_DRAIN_PERCENT_PER_SECOND = 0.06;

    private final SimulationSessionRepository sessionRepository;
    private final TelemetryRecordRepository telemetryRepository;
    private final DroneService droneService;
    private final AlertService alertService;
    private final WeatherService weatherService;
    private final SimpMessagingTemplate messagingTemplate;
    private final long tickIntervalMs;
    private final Map<Long, RuntimeState> states = new ConcurrentHashMap<>();

    public SimulatorEngine(
            SimulationSessionRepository sessionRepository,
            TelemetryRecordRepository telemetryRepository,
            DroneService droneService,
            AlertService alertService,
            WeatherService weatherService,
            SimpMessagingTemplate messagingTemplate,
            @Value("${simulator.tick-interval-ms}") long tickIntervalMs) {
        this.sessionRepository = sessionRepository;
        this.telemetryRepository = telemetryRepository;
        this.droneService = droneService;
        this.alertService = alertService;
        this.weatherService = weatherService;
        this.messagingTemplate = messagingTemplate;
        this.tickIntervalMs = tickIntervalMs;
    }

    public void start(Long sessionId) {
        SimulationSession session = sessionRepository.findById(sessionId)
                .orElseThrow();
        RuntimeState state = states.computeIfAbsent(sessionId, id -> new RuntimeState());
        if (state.initialized) {
            state.paused = false;
            return;
        }
        Drone drone = session.getDrone();
        state.latitude = drone.getLatitude();
        state.longitude = drone.getLongitude();
        state.altitudeM = Math.max(drone.getAltitudeM(), 30);
        state.batteryPercent = drone.getBatteryPercent();
        state.waypointIndex = 0;
        state.segmentProgress = 0;
        state.segmentTravelledMeters = 0;
        state.segmentInitialized = false;
        state.speedMs = 0;
        state.lowBatteryAlertSent = false;
        state.warningBatterySent = false;
        state.lastWeatherCode = null;
        state.initialized = true;
        state.paused = false;
        alertService.create(sessionId, drone.getId(), AlertLevel.INFO,
                "Симуляция «" + session.getName() + "» запущена");
    }

    public void pause(Long sessionId) {
        RuntimeState state = states.get(sessionId);
        if (state != null) {
            state.paused = true;
        }
    }

    public void stop(Long sessionId) {
        states.remove(sessionId);
    }

    public void publishSnapshot(Long sessionId) {
        RuntimeState state = states.get(sessionId);
        if (state == null) {
            return;
        }
        SimulationSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        Drone drone = session.getDrone();
        var waypoints = session.getWaypoints();
        int total = waypoints.size();
        double progress = total == 0 ? 0
                : ((state.waypointIndex + state.segmentProgress) / total) * 100;
        WeatherSnapshot weather = weatherService.current(state.latitude, state.longitude);

        TelemetryMessage message = new TelemetryMessage(
                sessionId,
                drone.getId(),
                drone.getCallsign(),
                Instant.now(),
                state.latitude,
                state.longitude,
                state.altitudeM,
                state.paused ? 0 : state.speedMs,
                state.headingDeg,
                state.pitchDeg,
                state.rollDeg,
                state.batteryPercent,
                state.waypointIndex,
                total,
                Math.min(100, progress),
                session.getStatus().name(),
                drone.getStatus().name(),
                weather.code(),
                weather.name(),
                weather.windSpeedMs(),
                weather.precipitationMmH(),
                weather.visibilityKm(),
                weather.speedMultiplier(),
                weather.batteryDrainMultiplier()
        );
        messagingTemplate.convertAndSend("/topic/telemetry", message);
    }

    @Scheduled(fixedRateString = "${simulator.tick-interval-ms}")
    @Transactional
    public void tick() {
        double deltaSeconds = tickIntervalMs / 1000.0;

        for (Map.Entry<Long, RuntimeState> entry : states.entrySet()) {
            Long sessionId = entry.getKey();
            RuntimeState state = entry.getValue();
            if (state.paused || !state.initialized) {
                continue;
            }

            SimulationSession session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null || session.getStatus() != SessionStatus.RUNNING) {
                states.remove(sessionId);
                continue;
            }

            Drone drone = session.getDrone();
            var waypoints = session.getWaypoints();
            if (waypoints.isEmpty()) {
                completeSession(session, state, false);
                continue;
            }

            if (state.waypointIndex >= waypoints.size()) {
                completeSession(session, state, false);
                continue;
            }

            Waypoint target = waypoints.get(state.waypointIndex);
            if (!state.segmentInitialized) {
                initializeSegment(state, target);
            }

            double previousLat = state.latitude;
            double previousLon = state.longitude;

            WeatherSnapshot weather = weatherService.current(state.latitude, state.longitude);
            maybeCreateWeatherAlert(sessionId, drone.getId(), state, weather);

            double targetSpeedMs = normalizeSpeed(target.getSpeedMs(), drone.getMaxSpeedMs())
                    * weather.speedMultiplier();
            double previousSpeedMs = state.speedMs;
            state.speedMs = approachSpeed(
                    state.speedMs,
                    targetSpeedMs,
                    accelerationMs2(drone.getMaxSpeedMs()) * weather.accelerationMultiplier() * deltaSeconds
            );

            if (state.segmentDistanceMeters <= MIN_SEGMENT_DISTANCE_METERS) {
                snapToTarget(state, target);
            } else {
                double tickDistanceMeters = ((previousSpeedMs + state.speedMs) / 2.0) * deltaSeconds;
                state.segmentTravelledMeters += tickDistanceMeters;
                state.segmentProgress = Math.min(1.0,
                        state.segmentTravelledMeters / state.segmentDistanceMeters);

                state.latitude = lerp(state.segmentStartLatitude, target.getLatitude(), state.segmentProgress);
                state.longitude = lerp(state.segmentStartLongitude, target.getLongitude(), state.segmentProgress);
                state.altitudeM = lerp(state.segmentStartAltitudeM, target.getAltitudeM(), state.segmentProgress);
            }

            state.headingDeg = bearing(previousLat, previousLon, target.getLatitude(), target.getLongitude());
            state.pitchDeg = Math.sin(state.segmentProgress * Math.PI) * 8 * weather.stabilityMultiplier();
            state.rollDeg = Math.cos(state.segmentProgress * Math.PI * 2) * 5 * weather.stabilityMultiplier();
            state.batteryPercent = Math.max(0,
                    state.batteryPercent - BATTERY_DRAIN_PERCENT_PER_SECOND
                            * weather.batteryDrainMultiplier() * deltaSeconds);

            double totalProgress = Math.min(100,
                    ((state.waypointIndex + state.segmentProgress) / waypoints.size()) * 100);

            if (state.batteryPercent < 15 && !state.lowBatteryAlertSent) {
                state.lowBatteryAlertSent = true;
                alertService.create(sessionId, drone.getId(), AlertLevel.CRITICAL,
                        "Критический заряд батареи: " + String.format("%.1f%%", state.batteryPercent));
            } else if (state.batteryPercent < 30 && !state.warningBatterySent) {
                state.warningBatterySent = true;
                alertService.create(sessionId, drone.getId(), AlertLevel.WARNING,
                        "Низкий заряд батареи: " + String.format("%.1f%%", state.batteryPercent));
            }

            droneService.updatePosition(drone.getId(), state.latitude, state.longitude, state.altitudeM, state.batteryPercent);

            TelemetryMessage message = new TelemetryMessage(
                    sessionId,
                    drone.getId(),
                    drone.getCallsign(),
                    Instant.now(),
                    state.latitude,
                    state.longitude,
                    state.altitudeM,
                    state.speedMs,
                    state.headingDeg,
                    state.pitchDeg,
                    state.rollDeg,
                    state.batteryPercent,
                    state.waypointIndex,
                    waypoints.size(),
                    totalProgress,
                    session.getStatus().name(),
                    drone.getStatus().name(),
                    weather.code(),
                    weather.name(),
                    weather.windSpeedMs(),
                    weather.precipitationMmH(),
                    weather.visibilityKm(),
                    weather.speedMultiplier(),
                    weather.batteryDrainMultiplier()
            );

            messagingTemplate.convertAndSend("/topic/telemetry", message);
            saveTelemetry(session, drone, state, totalProgress);

            if (state.segmentProgress >= 1.0) {
                state.waypointIndex++;
                state.segmentProgress = 0;
                state.segmentTravelledMeters = 0;
                state.segmentInitialized = false;
                if (state.waypointIndex >= waypoints.size()) {
                    completeSession(session, state, false);
                }
            }
        }
    }

    private void completeSession(SimulationSession session, RuntimeState state, boolean emergency) {
        session.setStatus(emergency ? SessionStatus.ABORTED : SessionStatus.COMPLETED);
        session.setFinishedAt(Instant.now());
        session.getDrone().setStatus(emergency ? DroneStatus.EMERGENCY : DroneStatus.IDLE);
        sessionRepository.save(session);
        alertService.create(session.getId(), session.getDrone().getId(), AlertLevel.INFO,
                emergency ? "Симуляция прервана" : "Миссия выполнена успешно");
        states.remove(session.getId());
    }

    private void maybeCreateWeatherAlert(Long sessionId, Long droneId, RuntimeState state, WeatherSnapshot weather) {
        if (weather.code().equals(state.lastWeatherCode)) {
            return;
        }
        state.lastWeatherCode = weather.code();
        if ("CLEAR".equals(weather.code())) {
            return;
        }

        AlertLevel level = switch (weather.riskLevel()) {
            case "CRITICAL" -> AlertLevel.CRITICAL;
            case "WARNING" -> AlertLevel.WARNING;
            default -> AlertLevel.INFO;
        };
        alertService.create(sessionId, droneId, level,
                "Погодные условия на маршруте: " + weather.name()
                        + ", скорость ограничена до "
                        + String.format("%.0f%%", weather.speedMultiplier() * 100)
                        + ", расход батареи ×"
                        + String.format("%.2f", weather.batteryDrainMultiplier()));
    }

    private void saveTelemetry(SimulationSession session, Drone drone, RuntimeState state, double progress) {
        TelemetryRecord record = new TelemetryRecord();
        record.setSessionId(session.getId());
        record.setDroneId(drone.getId());
        record.setLatitude(state.latitude);
        record.setLongitude(state.longitude);
        record.setAltitudeM(state.altitudeM);
        record.setSpeedMs(state.speedMs);
        record.setHeadingDeg(state.headingDeg);
        record.setPitchDeg(state.pitchDeg);
        record.setRollDeg(state.rollDeg);
        record.setBatteryPercent(state.batteryPercent);
        record.setWaypointIndex(state.waypointIndex);
        record.setProgressPercent(progress);
        telemetryRepository.save(record);
    }

    private static void initializeSegment(RuntimeState state, Waypoint target) {
        state.segmentStartLatitude = state.latitude;
        state.segmentStartLongitude = state.longitude;
        state.segmentStartAltitudeM = state.altitudeM;
        state.segmentDistanceMeters = distanceMeters(
                state.segmentStartLatitude,
                state.segmentStartLongitude,
                target.getLatitude(),
                target.getLongitude()
        );
        state.segmentTravelledMeters = 0;
        state.segmentProgress = 0;
        state.segmentInitialized = true;
    }

    private static void snapToTarget(RuntimeState state, Waypoint target) {
        state.latitude = target.getLatitude();
        state.longitude = target.getLongitude();
        state.altitudeM = target.getAltitudeM();
        state.segmentProgress = 1.0;
        state.segmentTravelledMeters = state.segmentDistanceMeters;
    }

    private static double normalizeSpeed(double targetSpeedMs, double maxSpeedMs) {
        double safeMaxSpeed = maxSpeedMs > 0 ? maxSpeedMs : targetSpeedMs;
        return Math.max(MIN_SIMULATION_SPEED_MS, Math.min(targetSpeedMs, safeMaxSpeed));
    }

    private static double accelerationMs2(double maxSpeedMs) {
        if (maxSpeedMs <= 0) {
            return MIN_ACCELERATION_MS2;
        }
        return Math.max(MIN_ACCELERATION_MS2, maxSpeedMs / SECONDS_TO_REACH_MAX_SPEED);
    }

    private static double approachSpeed(double currentSpeedMs, double targetSpeedMs, double maxDeltaMs) {
        if (currentSpeedMs < targetSpeedMs) {
            return Math.min(targetSpeedMs, currentSpeedMs + maxDeltaMs);
        }
        if (currentSpeedMs > targetSpeedMs) {
            return Math.max(targetSpeedMs, currentSpeedMs - maxDeltaMs);
        }
        return currentSpeedMs;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * Math.min(1, t);
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double startLat = Math.toRadians(lat1);
        double endLat = Math.toRadians(lat2);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * centralAngle;
    }

    private static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static class RuntimeState {
        boolean initialized;
        boolean paused;
        int waypointIndex;
        double segmentProgress;
        double segmentTravelledMeters;
        double segmentDistanceMeters;
        double segmentStartLatitude;
        double segmentStartLongitude;
        double segmentStartAltitudeM;
        boolean segmentInitialized;
        double latitude;
        double longitude;
        double altitudeM;
        double speedMs;
        double headingDeg;
        double pitchDeg;
        double rollDeg;
        double batteryPercent;
        boolean lowBatteryAlertSent;
        boolean warningBatterySent;
        String lastWeatherCode;
    }
}
