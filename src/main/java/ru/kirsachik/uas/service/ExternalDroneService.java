package ru.kirsachik.uas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kirsachik.uas.dto.ConnectDroneRequest;
import ru.kirsachik.uas.dto.ExternalTelemetryRequest;
import ru.kirsachik.uas.dto.TelemetryMessage;
import ru.kirsachik.uas.model.*;
import ru.kirsachik.uas.repository.DroneRepository;
import java.time.Instant;
import java.util.Objects;

@Service
public class ExternalDroneService {

    private final DroneRepository droneRepository;
    private final AlertService alertService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WeatherService weatherService;
    private final MqttDroneListener mqttDroneListener;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private ExternalDroneService self;

    public ExternalDroneService(
            DroneRepository droneRepository,
            AlertService alertService,
            SimpMessagingTemplate messagingTemplate,
            WeatherService weatherService,
            MqttDroneListener mqttDroneListener,
            ObjectMapper objectMapper) {
        this.droneRepository = droneRepository;
        this.alertService = alertService;
        this.messagingTemplate = messagingTemplate;
        this.weatherService = weatherService;
        this.mqttDroneListener = mqttDroneListener;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Drone connect(Long droneId, ConnectDroneRequest request) {
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new IllegalArgumentException("БПЛА не найден: " + droneId));
        if (drone.getStatus() == DroneStatus.FLYING || drone.getStatus() == DroneStatus.PAUSED) {
            throw new IllegalStateException("Сначала завершите текущий полёт или симуляцию");
        }

        drone.setSourceType(DroneSourceType.EXTERNAL);
        drone.setConnectionProtocol(request.protocol());
        drone.setEndpoint(request.endpoint().trim());
        drone.setApiKey(request.apiKey());
        drone.setExternalDeviceId(request.externalDeviceId());
        drone.setConnected(true);
        drone.setStatus(DroneStatus.READY);
        drone.setLastTelemetryAt(null);

        Drone saved = droneRepository.save(drone);

        if (request.protocol() == ConnectionProtocol.MQTT) {
            mqttDroneListener.subscribe(droneId, request.endpoint(), request.mqttTopic(), payload -> {
                try {
                    ExternalTelemetryRequest telemetry = objectMapper.readValue(payload, ExternalTelemetryRequest.class);
                    self.ingest(droneId, telemetry, null);
                } catch (Exception e) {
                    alertService.create(null, droneId, AlertLevel.WARNING,
                            "Ошибка разбора MQTT-телеметрии: " + e.getMessage());
                }
            });
        }

        alertService.create(null, droneId, AlertLevel.INFO,
                "Реальный БПЛА подключён (" + request.protocol() + ")");
        return saved;
    }

    @Transactional
    public Drone disconnect(Long droneId) {
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new IllegalArgumentException("БПЛА не найден: " + droneId));
        mqttDroneListener.unsubscribe(droneId);
        drone.setConnected(false);
        drone.setConnectionProtocol(ConnectionProtocol.NONE);
        drone.setStatus(DroneStatus.IDLE);
        droneRepository.save(drone);
        alertService.create(null, droneId, AlertLevel.INFO, "БПЛА отключён");
        return drone;
    }

    @Transactional
    public Drone ingest(Long droneId, ExternalTelemetryRequest request, String apiKey) {
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new IllegalArgumentException("БПЛА не найден: " + droneId));
        if (drone.getSourceType() != DroneSourceType.EXTERNAL || !drone.isConnected()) {
            throw new IllegalStateException("БПЛА не подключён как реальный");
        }
        if (apiKey != null) {
            validateApiKey(drone, apiKey);
        }

        drone.setLatitude(request.latitude());
        drone.setLongitude(request.longitude());
        drone.setAltitudeM(request.altitudeM());
        drone.setBatteryPercent(request.batteryPercent());
        drone.setLastTelemetryAt(Instant.now());
        drone.setStatus(request.speedMs() > 0.5 ? DroneStatus.FLYING : DroneStatus.READY);
        droneRepository.save(drone);

        WeatherSnapshot weather = weatherService.current(request.latitude(), request.longitude());
        TelemetryMessage message = new TelemetryMessage(
                null,
                drone.getId(),
                drone.getCallsign(),
                Instant.now(),
                request.latitude(),
                request.longitude(),
                request.altitudeM(),
                request.speedMs(),
                request.headingDeg(),
                request.pitchDeg(),
                request.rollDeg(),
                request.batteryPercent(),
                0,
                0,
                0,
                drone.getStatus().name(),
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
        return drone;
    }

    private void validateApiKey(Drone drone, String apiKey) {
        if (drone.getApiKey() != null && !drone.getApiKey().isBlank()) {
            if (!Objects.equals(drone.getApiKey(), apiKey)) {
                throw new IllegalArgumentException("Неверный API-ключ");
            }
        }
    }
}
