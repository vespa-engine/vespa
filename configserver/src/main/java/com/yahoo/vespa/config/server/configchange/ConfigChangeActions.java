// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeAction;

import java.util.List;

/**
 * Contains an aggregated view of which actions that must be performed to handle config
 * changes between the current active model and the next model to prepare.
 * The actions are split into restart and re-feed actions.
 *
 * @author geirst
 * @since 5.44
 */
public class ConfigChangeActions {

    private final RestartActions restartActions;
    private final RefeedActions refeedActions;

    public ConfigChangeActions() {
        this.restartActions = new RestartActions();
        this.refeedActions = new RefeedActions();
    }

    public ConfigChangeActions(List<ConfigChangeAction> actions) {
        this.restartActions = new RestartActions(actions);
        this.refeedActions = new RefeedActions(actions);
    }

    public RestartActions getRestartActions() {
        return restartActions;
    }

    public RefeedActions getRefeedActions() {
        return refeedActions;
    }

}
