// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.jira;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssues {
    public final List<JiraIssue> issues;

    @JsonCreator
    public JiraIssues(@JsonProperty("issues") List<JiraIssue> issues) {
        this.issues = issues == null ? Collections.emptyList() : issues;
    }
}
