// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEntry {

    public final long time;
    public final String level;
    public final String message;

    @JsonCreator
    public LogEntry(@JsonProperty("time") long time,
                    @JsonProperty("level") String level,
                    @JsonProperty("message") String message) {
        this.time = time;
        this.level = level;
        this.message = message;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "time=" + time +
                ", level='" + level + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
