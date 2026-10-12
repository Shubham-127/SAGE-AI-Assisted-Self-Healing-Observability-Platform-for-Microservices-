package com.sage.detector_service.model;

import java.util.List;

// One case file. Public fields are turned into JSON automatically for the dashboard.
public class Incident {
    public String id;                 // INC-001 ...
    public String rootService;        // the service that is REALLY broken
    public List<String> affected;     // services that only LOOK broken because of it
    public List<String> evidence;     // the reasoning lines
    public String suggestedAction;    // RESTART_POD or SCALE_UP
    public String status;             // OPEN or RESOLVED
    public String openedAt;
    public String resolvedAt;
}