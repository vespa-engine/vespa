// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.ImplementedBy;
import com.google.inject.Module;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.core.DefaultBindingSelector;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.service.NoBindingSetSelectedException;

import java.net.URI;

/**
 * This interface defines the component that is used by the {@link CurrentContainer} to assign a {@link BindingSet} to a
 * newly created {@link Container} based on the given {@link URI}. The default implementation of this interface returns
 * {@link BindingSet#DEFAULT} regardless of input. To specify your own selector you need to {@link
 * GuiceRepository#install(Module) install} a Guice {@link Module} that provides a binding for this interface.
 *
 * @author Simon Thoresen Hult
 */
@ImplementedBy(DefaultBindingSelector.class)
public interface BindingSetSelector {

    /**
     * Returns the name of the {@link BindingSet} to assign to the {@link Container} for the given {@link URI}. If this
     * method returns <em>null</em>, the corresponding call to {@link CurrentContainer#newReference(URI)} will throw a
     * {@link NoBindingSetSelectedException}.
     *
     * @param uri The URI to select on.
     * @return The name of selected BindingSet.
     */
    String select(URI uri);

}
