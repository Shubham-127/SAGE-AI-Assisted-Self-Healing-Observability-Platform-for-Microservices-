package com.sage.payment_service.controller;

import com.sage.payment_service.fault.FaultInjectionController;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")          // every URL here starts with /payments
public class PaymentController {

    // We need the fault switch, so we keep a reference to it.
    private final FaultInjectionController faultController;

    // Constructor: Spring automatically hands us the FaultInjectionController.
    // (This is called "dependency injection": we don't create it, Spring gives it to us.)
    public PaymentController(FaultInjectionController faultController) {
        this.faultController = faultController;
    }

    // URL: POST /payments/order-1
    // {orderRef} is a blank in the URL. Whatever is written there lands in the orderRef variable.
    @PostMapping("/{orderRef}")
    public Map<String, Object> charge(@PathVariable String orderRef) throws InterruptedException {

        // Ask the switch: are we supposed to be broken right now?
        FaultInjectionController.FaultType fault = faultController.getActiveFault();

        if (fault == FaultInjectionController.FaultType.SLOW) {
            Thread.sleep(5000);   // pause 5 seconds on purpose = pretend the payment gateway is slow
        }
        if (fault == FaultInjectionController.FaultType.EXCEPTION) {
            // Throwing an exception makes Spring send back an HTTP 500 error = pretend a crash
            throw new RuntimeException("SAGE simulated failure: payment gateway error");
        }

        // Healthy path: reply with a small piece of data (Spring turns the Map into JSON).
        return Map.of("status", "PAID", "orderRef", orderRef);
    }
}