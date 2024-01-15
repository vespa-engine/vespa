// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.application.validation.Validation.Context;

/**
 * Validates that a Vespa Cloud application has at least one container cluster.
 *
 * @author jonmv
 */
public class ContainerInCloudValidator implements Validator {

    @Override
    public void validate(Context context) {
        if (context.deployState().isHosted() && context.model().getContainerClusters().isEmpty())
            context.illegal("Vespa Cloud applications must have at least one container cluster");
    }

}
