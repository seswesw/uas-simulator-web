package ru.kirsachik.uas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kirsachik.uas.model.TelemetryRecord;
import java.util.List;

public interface TelemetryRecordRepository extends JpaRepository<TelemetryRecord, Long> {
    List<TelemetryRecord> findTop100BySessionIdOrderByTimestampDesc(Long sessionId);
}
