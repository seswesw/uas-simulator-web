package ru.kirsachik.uas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kirsachik.uas.model.Drone;
import ru.kirsachik.uas.model.DroneStatus;
import java.util.List;

public interface DroneRepository extends JpaRepository<Drone, Long> {
    boolean existsByCallsign(String callsign);

    @Query("SELECT COUNT(d) FROM Drone d WHERE d.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<DroneStatus> statuses);
}
