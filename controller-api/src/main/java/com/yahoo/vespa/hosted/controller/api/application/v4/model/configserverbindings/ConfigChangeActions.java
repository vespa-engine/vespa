// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigChangeActions {
    @JsonProperty("restart") public final List<RestartAction> restartActions;
    @JsonProperty("refeed") public final List<RefeedAction> refeedActions;
    @JsonProperty("reindex") public final List<ReindexAction> reindexActions;

    @JsonCreator
    public ConfigChangeActions(@JsonProperty("restart") List<RestartAction> restartActions,
                               @JsonProperty("refeed") List<RefeedAction> refeedActions,
                               @JsonProperty("reindex") List<ReindexAction> reindexActions) {
        this.restartActions = restartActions;
        this.refeedActions = refeedActions;
        this.reindexActions = reindexActions;
    }

    @Override
    public String toString() {
        return "ConfigChangeActions{" +
               "restartActions=" + restartActions +
               ", refeedActions=" + refeedActions +
               ", reindexActions=" + reindexActions +
               '}';
    }
}
