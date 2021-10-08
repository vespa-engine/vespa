// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.jira;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraComment {

    public final String body;

    @JsonCreator
    public JiraComment(@JsonProperty("body") String body) {
        this.body = body;
    }
}
