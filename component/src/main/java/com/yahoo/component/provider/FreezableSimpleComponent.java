// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider;

import com.yahoo.component.ComponentId;
import com.yahoo.component.AbstractComponent;

/**
 * Superclass for simple freezable components
 *
 * @author bratseth
 */
public class FreezableSimpleComponent extends AbstractComponent implements Freezable {

    /** True when this cannot be changed any more */
    private boolean frozen=false;

    protected FreezableSimpleComponent(ComponentId id) {
        super(id);
    }

    protected FreezableSimpleComponent() {}

    /**
     * Freezes this component to prevent further changes. Override this to freeze internal data
     * structures and dependent objects. Overrides must call super.
     * Calling freeze on an already frozen registry must have no effect.
     */
    public synchronized void freeze() { frozen=true; }

    /** Returns whether this is currently frozen */
    public final boolean isFrozen() { return frozen; }

    /** Throws an IllegalStateException if this is frozen */
    protected void ensureNotFrozen() {
        if (frozen)
            throw new IllegalStateException(this + " is frozen and cannot be modified");
    }

    /** Clones this. The clone will <i>not</i> be frozen */
    @Override
    public FreezableSimpleComponent clone() {
        FreezableSimpleComponent clone=(FreezableSimpleComponent)super.clone();
        clone.frozen = false;
        return clone;
    }

}
