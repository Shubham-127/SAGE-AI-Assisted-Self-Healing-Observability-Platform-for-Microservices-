package com.sage.payment_service.fault;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// @RestController = "this class answers web requests and returns data (not web pages)"
@RestController
// @RequestMapping = every URL in this class starts with /admin/fault
@RequestMapping("/admin/fault")
public class FaultInjectionController {

    // The 3 modes the service can be in.
    // NONE      = working normally
    // SLOW      = respond, but only after 5 seconds
    // EXCEPTION = crash immediately
    public enum FaultType { NONE, SLOW, EXCEPTION }

    // Remembers the current mode. Starts as NONE (healthy).
    // "volatile" = all web requests (which run on different threads)
    // always see the newest value, never an old copy.
    private volatile FaultType activeFault = FaultType.NONE;

    // URL: POST /admin/fault/inject?type=SLOW
    // @RequestParam reads the "type" part from the URL.
    @PostMapping("/inject")
    public String inject(@RequestParam FaultType type) {
        this.activeFault = type;                 // save the new mode
        return "Payment fault injected: " + type;
    }

    // URL: POST /admin/fault/clear  -> go back to healthy
    @PostMapping("/clear")
    public String clear() {
        this.activeFault = FaultType.NONE;
        return "Payment fault cleared";
    }

    // Other classes call this to ask "what mode are we in right now?"
    public FaultType getActiveFault() {
        return activeFault;
    }
}