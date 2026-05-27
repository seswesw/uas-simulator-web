package ru.kirsachik.uas.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "drones")
public class Drone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String callsign;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(name = "max_speed_ms", nullable = false)
    private double maxSpeedMs;

    @Column(name = "max_altitude_m", nullable = false)
    private double maxAltitudeM;

    @Column(name = "battery_capacity_mah", nullable = false)
    private double batteryCapacityMah;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DroneStatus status = DroneStatus.IDLE;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "altitude_m", nullable = false)
    private double altitudeM;

    @Column(name = "battery_percent", nullable = false)
    private double batteryPercent = 100.0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private DroneSourceType sourceType = DroneSourceType.SIMULATOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_protocol", nullable = false)
    private ConnectionProtocol connectionProtocol = ConnectionProtocol.NONE;

    @Column(length = 512)
    private String endpoint;

    @Column(name = "api_key", length = 64)
    private String apiKey;

    @Column(name = "external_device_id", length = 128)
    private String externalDeviceId;

    @Column(nullable = false)
    private boolean connected;

    @Column(name = "last_telemetry_at")
    private Instant lastTelemetryAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getMaxSpeedMs() {
        return maxSpeedMs;
    }

    public void setMaxSpeedMs(double maxSpeedMs) {
        this.maxSpeedMs = maxSpeedMs;
    }

    public double getMaxAltitudeM() {
        return maxAltitudeM;
    }

    public void setMaxAltitudeM(double maxAltitudeM) {
        this.maxAltitudeM = maxAltitudeM;
    }

    public double getBatteryCapacityMah() {
        return batteryCapacityMah;
    }

    public void setBatteryCapacityMah(double batteryCapacityMah) {
        this.batteryCapacityMah = batteryCapacityMah;
    }

    public DroneStatus getStatus() {
        return status;
    }

    public void setStatus(DroneStatus status) {
        this.status = status;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAltitudeM() {
        return altitudeM;
    }

    public void setAltitudeM(double altitudeM) {
        this.altitudeM = altitudeM;
    }

    public double getBatteryPercent() {
        return batteryPercent;
    }

    public void setBatteryPercent(double batteryPercent) {
        this.batteryPercent = batteryPercent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public DroneSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(DroneSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public ConnectionProtocol getConnectionProtocol() {
        return connectionProtocol;
    }

    public void setConnectionProtocol(ConnectionProtocol connectionProtocol) {
        this.connectionProtocol = connectionProtocol;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getExternalDeviceId() {
        return externalDeviceId;
    }

    public void setExternalDeviceId(String externalDeviceId) {
        this.externalDeviceId = externalDeviceId;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public Instant getLastTelemetryAt() {
        return lastTelemetryAt;
    }

    public void setLastTelemetryAt(Instant lastTelemetryAt) {
        this.lastTelemetryAt = lastTelemetryAt;
    }
}
