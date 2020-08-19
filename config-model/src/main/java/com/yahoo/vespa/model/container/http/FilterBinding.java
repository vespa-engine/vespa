// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.vespa.model.container.component.BindingPattern;

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

    public static FilterBinding create(ComponentSpecification filterId, BindingPattern binding) {
        return new FilterBinding(filterId, binding);
    }

    public ComponentSpecification filterId() {
        return filterId;
    }

    public BindingPattern binding() {
        return binding;
    }

}
