package com.sage.payment_service.controller;

import com.sage.payment_service.fault.FaultInjectionController;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final FaultInjectionController faultController;

    public PaymentController(FaultInjectionController faultController) {
        this.faultController = faultController;
    }

    @PostMapping("/{orderRef}")
    public Map<String, Object> charge(@PathVariable String orderRef) throws InterruptedException {

        FaultInjectionController.FaultType fault = faultController.getActiveFault();

        if (fault == FaultInjectionController.FaultType.SLOW) {
            Thread.sleep(5000);   // slow payment gateway
        }
        if (fault == FaultInjectionController.FaultType.EXCEPTION) {
            throw new RuntimeException("SAGE simulated failure: payment gateway error");
        }
        if (fault == FaultInjectionController.FaultType.DB_POOL_EXHAUSTED) {
            Thread.sleep(8000);   // waits for a database connection that never comes...
            throw new RuntimeException("SAGE simulated failure: payment DB connection pool exhausted");
        }

        return Map.of("status", "PAID", "orderRef", orderRef);
    }
}