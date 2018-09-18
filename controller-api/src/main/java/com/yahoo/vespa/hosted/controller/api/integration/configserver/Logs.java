package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.util.Map;

public class Logs {

    private final Map<String, String> logs;

    public Logs(Map<String, String> logs) {
        this.logs = logs;
    }

    public Map<String, String> logs() {
        return this.logs;
    }
}
