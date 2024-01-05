// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.application.validation.Validator;

import java.util.List;

/**
 * Interface for validating changes between a current active and next config model.
 *
 * @author geirst
 */
public interface ChangeValidator {

    /**
     * Validates changes from the previous to the next model. Necessary actions by the user
     * should be reported through the context; see {@link Validator} for more details.
     */
    void validate(ChangeContext context);

}
