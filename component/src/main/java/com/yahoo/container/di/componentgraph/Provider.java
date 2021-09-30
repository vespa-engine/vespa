// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph;

import com.yahoo.component.Deconstructable;

/**
 * <p>Provides a component of the parameter type T.
 * If (and only if) dependency injection does not have a component of type T,
 * it will request one from the Provider providing type T.</p>
 *
 * <p>Providers are useful in these situations:</p>
 * <ul>
 *     <li>Some code is needed to create the component instance in question.</li>
 *     <li>The component creates resources that must be deconstructed.</li>
 *     <li>A fallback component should be provided in case the application (or system)
 *     does not provide a component instance.</li>
 * </ul>
 *
 * @author Tony Vaagenes
 * @author gjoranv
 */
public interface Provider<T> extends Deconstructable {

    T get();

}
