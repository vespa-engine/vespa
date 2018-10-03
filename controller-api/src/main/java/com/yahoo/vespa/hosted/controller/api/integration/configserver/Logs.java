// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
