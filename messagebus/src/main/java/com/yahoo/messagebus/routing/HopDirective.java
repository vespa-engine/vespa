// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * This class is the base class for the primitives that make up a {@link Hop}'s selector.
 *
 * @author Simon Thoresen Hult
 */
public interface HopDirective {

    /**
     * Returns true if this directive matches another.
     *
     * @param dir The directive to compare this to.
     * @return True if this matches the argument.
     */
    public boolean matches(HopDirective dir);

    /**
     * Returns a string representation of this that can be debugged but not parsed.
     *
     * @return The debug string.
     */
    public String toDebugString();
}

