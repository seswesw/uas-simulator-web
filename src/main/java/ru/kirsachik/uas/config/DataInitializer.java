package ru.kirsachik.uas.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.kirsachik.uas.model.Drone;
import ru.kirsachik.uas.model.DroneStatus;
import ru.kirsachik.uas.repository.DroneRepository;
import ru.kirsachik.uas.service.UserService;

@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private final DroneRepository droneRepository;
    private final UserService userService;

    public DataInitializer(DroneRepository droneRepository, UserService userService) {
        this.droneRepository = droneRepository;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        userService.ensureDefaultUsers();

        if (droneRepository.count() > 0) {
            return;
        }

        createDrone("UAV-ALPHA", "Orlan-10", 25, 1200, 5000, 55.751244, 37.618423);
        createDrone("UAV-BRAVO", "Supercam S350", 18, 800, 4200, 55.760000, 37.630000);
        createDrone("UAV-CHARLIE", "ZALA 421-16E", 22, 1000, 4800, 55.740000, 37.600000);
    }

    private void createDrone(String callsign, String model, double speed, double alt, double battery, double lat, double lon) {
        Drone drone = new Drone();
        drone.setCallsign(callsign);
        drone.setModel(model);
        drone.setMaxSpeedMs(speed);
        drone.setMaxAltitudeM(alt);
        drone.setBatteryCapacityMah(battery);
        drone.setLatitude(lat);
        drone.setLongitude(lon);
        drone.setAltitudeM(0);
        drone.setBatteryPercent(100);
        drone.setStatus(DroneStatus.READY);
        droneRepository.save(drone);
    }
}
