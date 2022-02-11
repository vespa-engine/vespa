// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.List;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public interface ComponentDeconstructor {

    /** Deconstructs the given components in order, then the given bundles. */
    void deconstruct(long generation, List<Object> components, Collection<Bundle> bundles);

    /** Wait for all previous destruction tasks to complete */
    default void shutdown() {}

}
