package com.xianfupu.shop.admin;

import com.xianfupu.shop.monitoring.MonitoringService;
import com.xianfupu.shop.security.AuthSessionService;
import com.xianfupu.shop.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationsController {
    private final MonitoringService monitoring;
    private final AuthSessionService sessions;

    public AdminOperationsController(MonitoringService monitoring, AuthSessionService sessions) {
        this.monitoring = monitoring;
        this.sessions = sessions;
    }

    @GetMapping("/errors")
    List<MonitoringService.ErrorLogView> errors(@RequestParam(defaultValue = "100") int limit) {
        return monitoring.errors(limit);
    }

    @GetMapping("/alerts")
    List<MonitoringService.AlertView> alerts(@RequestParam(defaultValue = "ALL") String status,
                                             @RequestParam(defaultValue = "100") int limit) {
        return monitoring.alerts(status, limit);
    }

    @PatchMapping("/alerts/{id}/acknowledge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void acknowledge(@PathVariable long id) {
        monitoring.acknowledge(id, CurrentUser.id());
    }

    @GetMapping("/payment-callbacks")
    List<MonitoringService.PaymentCallbackView> callbacks(@RequestParam(defaultValue = "100") int limit) {
        return monitoring.callbacks(limit);
    }

    @GetMapping("/sessions")
    List<AuthSessionService.SessionView> sessions(@RequestParam(defaultValue = "ADMIN") String role) {
        return sessions.list(role);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable String sessionId) {
        sessions.revoke(sessionId);
    }
}
