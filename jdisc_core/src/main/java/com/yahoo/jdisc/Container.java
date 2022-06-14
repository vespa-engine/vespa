// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.google.inject.ConfigurationException;
import com.google.inject.ProvisionException;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.service.ServerProvider;

import java.net.URI;

/**
 * <p>This is the immutable Container. An instance of this class is attached to every {@link Request}, and as long as
 * the {@link Request#release()} method has not been called, that Container instance is actively kept alive to prevent
 * any race conditions during reconfiguration or shutdown. At any time there is only a single active Container in the
 * running {@link Application}, and the only way to retrieve a reference to that Container is by calling {@link
 * CurrentContainer#newReference(URI)}. Instead of holding a local Container object inside a {@link ServerProvider}
 * (which will eventually become stale), use the {@link Request#Request(CurrentContainer, URI) appropriate Request
 * constructor} instead.</p>
 *
 * <p>The only way to <u>create</u> a new instance of this class is to 1) create and configure a {@link
 * ContainerBuilder}, and 2) pass that to the {@link ContainerActivator#activateContainer(ContainerBuilder)} method.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface Container extends SharedResource, Timer {

    /**
     * Attempts to find a {@link RequestHandler} in the current server- (if {@link Request#isServerRequest()} is
     * <em>true</em>) or client- (if {@link Request#isServerRequest()} is <em>false</em>) {@link BindingSet} that
     * matches the given {@link URI}. If no match can be found, this method returns null.
     *
     * @param request The Request to match against the bound {@link RequestHandler}s.
     * @return The matching RequestHandler, or null if there is no match.
     */
    RequestHandler resolveHandler(Request request);

    /**
     * Returns the appropriate instance for the given injection type. When feasible, avoid using this method in
     * favor of having Guice inject your dependencies ahead of time.
     *
     * @param type The class object of the instance to return.
     * @param <T>  The class of the instance to return.
     * @return The appropriate instance of the given class.
     * @throws ConfigurationException If this injector cannot find or create the provider.
     * @throws ProvisionException     If there was a runtime failure while providing an instance.
     */
    <T> T getInstance(Class<T> type);

}
