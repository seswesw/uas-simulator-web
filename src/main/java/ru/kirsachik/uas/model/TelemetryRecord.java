package ru.kirsachik.uas.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "telemetry_records", indexes = {
        @Index(name = "idx_telemetry_session", columnList = "session_id")
})
public class TelemetryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "drone_id", nullable = false)
    private Long droneId;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    private double latitude;
    private double longitude;

    @Column(name = "altitude_m")
    private double altitudeM;

    @Column(name = "speed_ms")
    private double speedMs;

    @Column(name = "heading_deg")
    private double headingDeg;

    @Column(name = "pitch_deg")
    private double pitchDeg;

    @Column(name = "roll_deg")
    private double rollDeg;

    @Column(name = "battery_percent")
    private double batteryPercent;

    @Column(name = "waypoint_index")
    private int waypointIndex;

    @Column(name = "progress_percent")
    private double progressPercent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getDroneId() {
        return droneId;
    }

    public void setDroneId(Long droneId) {
        this.droneId = droneId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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

    public double getSpeedMs() {
        return speedMs;
    }

    public void setSpeedMs(double speedMs) {
        this.speedMs = speedMs;
    }

    public double getHeadingDeg() {
        return headingDeg;
    }

    public void setHeadingDeg(double headingDeg) {
        this.headingDeg = headingDeg;
    }

    public double getPitchDeg() {
        return pitchDeg;
    }

    public void setPitchDeg(double pitchDeg) {
        this.pitchDeg = pitchDeg;
    }

    public double getRollDeg() {
        return rollDeg;
    }

    public void setRollDeg(double rollDeg) {
        this.rollDeg = rollDeg;
    }

    public double getBatteryPercent() {
        return batteryPercent;
    }

    public void setBatteryPercent(double batteryPercent) {
        this.batteryPercent = batteryPercent;
    }

    public int getWaypointIndex() {
        return waypointIndex;
    }

    public void setWaypointIndex(int waypointIndex) {
        this.waypointIndex = waypointIndex;
    }

    public double getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(double progressPercent) {
        this.progressPercent = progressPercent;
    }
}
