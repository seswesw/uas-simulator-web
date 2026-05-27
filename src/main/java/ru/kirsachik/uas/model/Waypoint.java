package ru.kirsachik.uas.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "waypoints")
public class Waypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private SimulationSession session;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "altitude_m", nullable = false)
    private double altitudeM;

    @Column(name = "speed_ms", nullable = false)
    private double speedMs;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @JsonIgnore
    public SimulationSession getSession() {
        return session;
    }

    public void setSession(SimulationSession session) {
        this.session = session;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(int sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
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
}
