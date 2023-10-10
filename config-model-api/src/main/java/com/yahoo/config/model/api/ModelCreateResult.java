// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.List;

/**
 * The result after creating and validating a Model.
 *
 * @author geirst
 */
public class ModelCreateResult {

    private final Model model;
    private final List<ConfigChangeAction> configChangeActions;

    public ModelCreateResult(Model model, List<ConfigChangeAction> configChangeActions) {
        this.model = model;
        this.configChangeActions = configChangeActions;
    }

    /** The model these changes apply to */
    public Model getModel() { return model; }

    /** Returns the actions that needs to be done to successfully start using the new model */
    public List<ConfigChangeAction> getConfigChangeActions() { return configChangeActions; }

}
