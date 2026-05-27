package ru.kirsachik.uas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kirsachik.uas.dto.DroneRequest;
import ru.kirsachik.uas.model.Drone;
import ru.kirsachik.uas.model.DroneStatus;
import ru.kirsachik.uas.repository.DroneRepository;
import java.util.List;

@Service
public class DroneService {

    private final DroneRepository droneRepository;
    private final double homeLatitude;
    private final double homeLongitude;

    public DroneService(
            DroneRepository droneRepository,
            @Value("${simulator.home-latitude}") double homeLatitude,
            @Value("${simulator.home-longitude}") double homeLongitude) {
        this.droneRepository = droneRepository;
        this.homeLatitude = homeLatitude;
        this.homeLongitude = homeLongitude;
    }

    @Transactional(readOnly = true)
    public List<Drone> findAll() {
        return droneRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Drone findById(Long id) {
        return droneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("БПЛА не найден: " + id));
    }

    @Transactional
    public Drone create(DroneRequest request) {
        if (droneRepository.existsByCallsign(request.callsign())) {
            throw new IllegalArgumentException("Позывной уже используется: " + request.callsign());
        }
        Drone drone = new Drone();
        drone.setCallsign(request.callsign());
        drone.setModel(request.model());
        drone.setMaxSpeedMs(request.maxSpeedMs());
        drone.setMaxAltitudeM(request.maxAltitudeM());
        drone.setBatteryCapacityMah(request.batteryCapacityMah());
        drone.setLatitude(request.latitude() != 0 ? request.latitude() : homeLatitude);
        drone.setLongitude(request.longitude() != 0 ? request.longitude() : homeLongitude);
        drone.setAltitudeM(0);
        drone.setStatus(DroneStatus.READY);
        return droneRepository.save(drone);
    }

    @Transactional
    public void delete(Long id) {
        droneRepository.delete(findById(id));
    }

    @Transactional
    public Drone updateStatus(Long id, DroneStatus status) {
        Drone drone = findById(id);
        drone.setStatus(status);
        return droneRepository.save(drone);
    }

    @Transactional
    public Drone updatePosition(Long id, double lat, double lon, double alt, double battery) {
        Drone drone = findById(id);
        drone.setLatitude(lat);
        drone.setLongitude(lon);
        drone.setAltitudeM(alt);
        drone.setBatteryPercent(battery);
        return droneRepository.save(drone);
    }
}
