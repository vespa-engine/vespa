// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;

import java.io.Reader;
import java.util.Optional;

/**
 * Validate validation overrides (validation-overrides.xml). Done as a validator to make sure this is
 * done when validating the mode and not when building the model
 *
 * @author hmusum
 */
public class ValidationOverridesValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        Optional<Reader> overrides = deployState.getApplicationPackage().getValidationOverrides();
        if (overrides.isEmpty()) return;

        ValidationOverrides validationOverrides = ValidationOverrides.fromXml(overrides.get());
        validationOverrides.validate(deployState.now());
    }

}
