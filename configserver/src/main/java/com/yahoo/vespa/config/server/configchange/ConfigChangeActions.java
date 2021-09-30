// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeAction;

import java.util.List;
import java.util.Objects;

/**
 * Contains an aggregated view of which actions that must be performed to handle config
 * changes between the current active model and the next model to prepare.
 * The actions are split into restart and re-feed actions.
 *
 * @author geirst
 */
public class ConfigChangeActions {

    private final RestartActions restartActions;
    private final RefeedActions refeedActions;
    private final ReindexActions reindexActions;

    public ConfigChangeActions() {
        this(new RestartActions(), new RefeedActions(), new ReindexActions());
    }

    public ConfigChangeActions(List<ConfigChangeAction> actions) {
        this(new RestartActions(actions), new RefeedActions(actions), new ReindexActions(actions));
    }

    public ConfigChangeActions(RestartActions restartActions, RefeedActions refeedActions, ReindexActions reindexActions) {
        this.restartActions = Objects.requireNonNull(restartActions);
        this.refeedActions = Objects.requireNonNull(refeedActions);
        this.reindexActions = Objects.requireNonNull(reindexActions);
    }

    public RestartActions getRestartActions() {
        return restartActions;
    }

    public RefeedActions getRefeedActions() {
        return refeedActions;
    }

    public ReindexActions getReindexActions() { return reindexActions; }

}
