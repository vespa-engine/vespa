// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.container.component.BindingPattern;

import java.util.logging.Level;

/**
 * @author bjorncs
 */
public class FilterBinding {

    private final ComponentSpecification filterId;
    private final BindingPattern binding;

    private FilterBinding(ComponentSpecification filterId, BindingPattern binding) {
        this.filterId = filterId;
        this.binding = binding;
    }

    public static FilterBinding create(ComponentSpecification filterId, BindingPattern binding, DeployLogger logger) {
        if (binding.scheme().equals("https")) {
            logger.log(Level.WARNING, String.format("For binding '%s' on '%s': 'https' bindings are deprecated, " +
                                                    "use 'http' instead to bind to both http and https traffic.",
                                                    binding, filterId));
        }
        return new FilterBinding(filterId, binding);
    }

    public ComponentSpecification filterId() {
        return filterId;
    }

    public BindingPattern binding() {
        return binding;
    }

}
