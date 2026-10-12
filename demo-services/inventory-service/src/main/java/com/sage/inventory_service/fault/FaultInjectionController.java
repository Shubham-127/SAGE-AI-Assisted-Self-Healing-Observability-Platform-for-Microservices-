package com.sage.inventory_service.fault;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping("/admin/fault")
public class FaultInjectionController {

    public enum FaultType { NONE, SLOW, EXCEPTION, DB_POOL_EXHAUSTED }

    private volatile FaultType activeFault = FaultType.NONE;

    @PostMapping("/inject")
    public String inject(@RequestParam FaultType type) {
        this.activeFault = type;
        return "SAGE fault injected: " + type;
    }

    @PostMapping("/clear")
    public String clear() {
        this.activeFault = FaultType.NONE;
        return "Fault cleared";
    }

    public FaultType getActiveFault() {
        return activeFault;
    }
}