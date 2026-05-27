package ru.kirsachik.uas.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.kirsachik.uas.dto.SessionRequest;
import ru.kirsachik.uas.model.SimulationSession;
import ru.kirsachik.uas.model.TelemetryRecord;
import ru.kirsachik.uas.repository.TelemetryRecordRepository;
import ru.kirsachik.uas.service.SessionService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final TelemetryRecordRepository telemetryRepository;

    public SessionController(SessionService sessionService, TelemetryRecordRepository telemetryRepository) {
        this.sessionService = sessionService;
        this.telemetryRepository = telemetryRepository;
    }

    @GetMapping
    public List<SimulationSession> list() {
        return sessionService.findAll();
    }

    @GetMapping("/{id}")
    public SimulationSession get(@PathVariable Long id) {
        return sessionService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationSession create(@Valid @RequestBody SessionRequest request) {
        return sessionService.create(request);
    }

    @PostMapping("/{id}/start")
    public SimulationSession start(@PathVariable Long id) {
        return sessionService.start(id);
    }

    @PostMapping("/{id}/repeat")
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationSession repeat(@PathVariable Long id) {
        return sessionService.repeat(id);
    }

    @PostMapping("/{id}/pause")
    public SimulationSession pause(@PathVariable Long id) {
        return sessionService.pause(id);
    }

    @PostMapping("/{id}/resume")
    public SimulationSession resume(@PathVariable Long id) {
        return sessionService.resume(id);
    }

    @PostMapping("/{id}/stop")
    public SimulationSession stop(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean emergency) {
        return sessionService.stop(id, emergency);
    }

    @GetMapping("/{id}/telemetry")
    public List<TelemetryRecord> telemetry(@PathVariable Long id) {
        return telemetryRepository.findTop100BySessionIdOrderByTimestampDesc(id);
    }
}
