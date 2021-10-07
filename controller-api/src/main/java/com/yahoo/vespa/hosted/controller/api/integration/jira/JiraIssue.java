// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.jira;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Date;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssue {
    public final String key;
    private final Fields fields;

    @JsonCreator
    public JiraIssue(@JsonProperty("key") String key, @JsonProperty("fields") Fields fields) {
        this.key = key;
        this.fields = fields;
    }

    public Instant lastUpdated() {
        return fields.lastUpdated;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        final Instant lastUpdated;

        @JsonCreator
        public Fields(
                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'hh:mm:ss.SSSZ", timezone = "UTC")
                @JsonProperty("updated") Date updated) {
            lastUpdated = updated.toInstant();
        }

        public Fields(Instant instant) {
            this.lastUpdated = instant;
        }
    }
}
