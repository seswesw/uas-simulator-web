package ru.kirsachik.uas.web;

import org.springframework.web.bind.annotation.*;
import ru.kirsachik.uas.dto.DashboardStats;
import ru.kirsachik.uas.model.Alert;
import ru.kirsachik.uas.service.AlertService;
import ru.kirsachik.uas.service.DashboardService;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AlertService alertService;

    public DashboardController(DashboardService dashboardService, AlertService alertService) {
        this.dashboardService = dashboardService;
        this.alertService = alertService;
    }

    @GetMapping("/dashboard")
    public DashboardStats dashboard() {
        return dashboardService.getStats();
    }

    @GetMapping("/alerts")
    public List<Alert> alerts() {
        return alertService.findRecent();
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public Alert acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id);
    }
}
