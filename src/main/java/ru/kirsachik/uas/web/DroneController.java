package ru.kirsachik.uas.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.kirsachik.uas.dto.ConnectDroneRequest;
import ru.kirsachik.uas.dto.DroneRequest;
import ru.kirsachik.uas.dto.ExternalTelemetryRequest;
import ru.kirsachik.uas.model.Drone;
import ru.kirsachik.uas.service.DroneService;
import ru.kirsachik.uas.service.ExternalDroneService;
import java.util.List;

@RestController
@RequestMapping("/api/drones")
public class DroneController {

    private final DroneService droneService;
    private final ExternalDroneService externalDroneService;

    public DroneController(DroneService droneService, ExternalDroneService externalDroneService) {
        this.droneService = droneService;
        this.externalDroneService = externalDroneService;
    }

    @GetMapping
    public List<Drone> list() {
        return droneService.findAll();
    }

    @GetMapping("/{id}")
    public Drone get(@PathVariable Long id) {
        return droneService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Drone create(@Valid @RequestBody DroneRequest request) {
        return droneService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            externalDroneService.disconnect(id);
        } catch (Exception ignored) {
            // БПЛА мог быть не подключён
        }
        droneService.delete(id);
    }

    @PostMapping("/{id}/connect")
    public Drone connect(@PathVariable Long id, @Valid @RequestBody ConnectDroneRequest request) {
        return externalDroneService.connect(id, request);
    }

    @PostMapping("/{id}/disconnect")
    public Drone disconnect(@PathVariable Long id) {
        return externalDroneService.disconnect(id);
    }

    @PostMapping("/{id}/telemetry")
    public Drone ingestTelemetry(
            @PathVariable Long id,
            @Valid @RequestBody ExternalTelemetryRequest request,
            @RequestHeader(value = "X-Drone-Key", required = false) String apiKey) {
        return externalDroneService.ingest(id, request, apiKey);
    }
}
