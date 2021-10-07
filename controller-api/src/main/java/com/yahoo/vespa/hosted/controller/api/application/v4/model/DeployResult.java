// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;

import java.util.List;

/**
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployResult {

    public final RevisionId revisionId;
    public final Long applicationZipSize;
    public final List<LogEntry> prepareMessages;
    public final ConfigChangeActions configChangeActions;

    @JsonCreator
    public DeployResult(@JsonProperty("revisionId") RevisionId revisionId,
                        @JsonProperty("applicationZipSize") Long applicationZipSize,
                        @JsonProperty("prepareMessages") List<LogEntry> prepareMessages,
                        @JsonProperty("configChangeActions") ConfigChangeActions configChangeActions) {
        this.revisionId = revisionId;
        this.applicationZipSize = applicationZipSize;
        this.prepareMessages = prepareMessages;
        this.configChangeActions = configChangeActions;
    }

    @Override
    public String toString() {
        return "DeployResult{" +
                "revisionId=" + revisionId.id() +
                ", applicationZipSize=" + applicationZipSize +
                ", prepareMessages=" + prepareMessages +
                ", configChangeActions=" + configChangeActions +
                '}';
    }
}
