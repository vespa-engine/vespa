// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.model;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;

/**
 * For using non-component model classes with ComponentRegistry.
 *
 * @author Tony Vaagenes
 */
public final class ComponentAdaptor<T> extends AbstractComponent {

    public final T model;

    @SuppressWarnings("deprecation")
    public ComponentAdaptor(ComponentId globalComponentId, T model) {
        super(globalComponentId);
        this.model = model;
    }

    public static <T> ComponentAdaptor<T> create(ComponentId globalComponentId, T model) {
        return new ComponentAdaptor<>(globalComponentId, model);
    }

    // For testing
    T model() {
        return model;
    }

}
