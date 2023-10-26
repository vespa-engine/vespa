// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider;

/**
 * A class which may be irreversibly frozen. Any attempt to change the state of this class after
 * freezing throws an IllegalStateException.
 *
 * @author bratseth
 */
public interface Freezable {

    /**
     * Freezes this component to prevent further changes. Override this to freeze internal data
     * structures and dependent objects. Overrides must call super.
     * Calling freeze on an already frozen class must have no effect.
     */
    void freeze();


    /**
     * Inspect whether this object can be changed. If the object is immutable
     * from construction, this should return true, even if freeze() never has
     * been invoked.
     *
     * @return true if this instance is in an immutable state, false otherwise
     * @since 5.1.4
     */
    boolean isFrozen();

}
