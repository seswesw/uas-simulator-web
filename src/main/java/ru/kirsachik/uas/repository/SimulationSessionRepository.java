package ru.kirsachik.uas.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kirsachik.uas.model.SessionStatus;
import ru.kirsachik.uas.model.SimulationSession;
import java.util.List;
import java.util.Optional;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, Long> {

    @EntityGraph(attributePaths = {"drone", "waypoints"})
    List<SimulationSession> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"drone", "waypoints"})
    Optional<SimulationSession> findById(Long id);

    /** Без fetch коллекции waypoints — иначе LIMIT применяется в памяти (HHH90003004). */
    @Query("SELECT s FROM SimulationSession s WHERE s.status IN :statuses ORDER BY s.startedAt DESC")
    List<SimulationSession> findSessionsByStatusInOrderByStartedAtDesc(
            @Param("statuses") List<SessionStatus> statuses,
            Pageable pageable);

    default Optional<SimulationSession> findActiveSession() {
        List<SimulationSession> found = findSessionsByStatusInOrderByStartedAtDesc(
                List.of(SessionStatus.RUNNING, SessionStatus.PAUSED),
                Pageable.ofSize(1));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Query("SELECT COUNT(s) FROM SimulationSession s WHERE s.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<SessionStatus> statuses);
}
