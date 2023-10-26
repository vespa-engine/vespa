// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;


/**
 * <p>A named, versioned, identifiable component.
 * Components can by default be ordered by their id order. Their identity is defined by the id.</p>
 *
 * <p>Container components to be created via dependency injection do not need to implement this interface.</p>
 *
 * @author bratseth
 */
public interface Component extends Comparable<Component> {

    /** Initializes this. Always called from a constructor or the framework. Do not call. */
    void initId(ComponentId id);

    /** Returns the id of this component */
    ComponentId getId();

}
