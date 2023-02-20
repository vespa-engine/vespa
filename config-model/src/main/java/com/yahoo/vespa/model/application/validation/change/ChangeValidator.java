// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationOverrides;

import java.time.Instant;
import java.util.List;

/**
 * Interface for validating changes between a current active and next config model.
 *
 * @author geirst
 */
public interface ChangeValidator {

    /**
     * Validates the current active vespa model with the next model.
     * Both current and next should be non-null.
     *
     * @param current the current active model
     * @param next the next model we would like to activate
     * @return a list of actions specifying what needs to be done in order to activate the new model.
     *         Return an empty list if nothing needs to be done
     * @throws IllegalArgumentException if the change fails validation
     */
    List<ConfigChangeAction> validate(VespaModel current, VespaModel next, DeployState deployState);

}
