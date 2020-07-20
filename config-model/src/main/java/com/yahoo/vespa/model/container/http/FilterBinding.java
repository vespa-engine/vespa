// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.application.api.DeployLogger;

import java.util.logging.Level;

/**
 * @author bjorncs
 */
public class FilterBinding {

    private final ComponentSpecification filterId;
    private final String binding;

    private FilterBinding(ComponentSpecification filterId, String binding) {
        this.filterId = filterId;
        this.binding = binding;
    }

    public static FilterBinding create(ComponentSpecification filterId, String binding, DeployLogger logger) {
        if (binding.startsWith("https://")) {
            logger.log(Level.WARNING, String.format("For binding '%s' on '%s': 'https' bindings are deprecated, " +
                                                    "use 'http' instead to bind to both http and https traffic.",
                                                    binding, filterId));
        }
        return new FilterBinding(filterId, binding);
    }

    public ComponentSpecification filterId() {
        return filterId;
    }

    public String binding() {
        return binding;
    }

}
