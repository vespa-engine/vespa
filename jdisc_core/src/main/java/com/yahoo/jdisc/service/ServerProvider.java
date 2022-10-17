// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.ServerRepository;

import java.net.URI;

/**
 * <p>This interface defines a component that is capable of acting as a server for an external client. To activate a
 * ServerProvider it must be {@link ServerRepository#install(ServerProvider) installed} in a {@link ContainerBuilder},
 * and that builder must be {@link ContainerActivator#activateContainer(ContainerBuilder) activated}.</p>
 *
 * <p>If a ServerProvider is to expire due to {@link Application} reconfiguration, it is necessary to close() that
 * ServerProvider before deactivating the owning {@link Container}. Typically:</p>
 *
 * <pre>
 * myExpiredServers.close();
 * reconfiguredContainerBuilder.servers().install(myRetainedServers);
 * containerActivator.activateContainer(reconfiguredContainerBuilder);
 * </pre>
 *
 * <p>All implementations of this interface will need to have a {@link CurrentContainer} injected into its constructor
 * so that it is able to create and dispatch new {@link Request}s.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface ServerProvider extends SharedResource {

    /**
     * <p>This is a synchronous method to configure this ServerProvider and bind the listen port (or equivalent). The
     * {@link Container} does <em>not</em> call this method, instead it is a required step in the {@link Application}
     * initialization code.</p>
     */
    void start();

    /**
     * <p>This is a synchronous method to close the listen port (or equivalent) of this ServerProvider and flush any
     * input buffers that will cause calls to {@link CurrentContainer#newReference(URI)}. This method <em>must not</em>
     * return until the implementation can guarantee that there will be no further calls to CurrentContainer. All
     * previously dispatched {@link Request}s are processed as before.</p>
     *
     * <p>The {@link Container} does <em>not</em> call this method, instead it is a required step in the {@link
     * Application} shutdown code.</p>
     */
    void close();

    /**
     * Whether multiple instances of this can coexist, by means of a multiplexer on top of any exclusive resource.
     * If this is true, new instances to replace old ones, during a graph generation switch, will be started before
     * the obsolete ones are stopped; otherwise, the old will be stopped, and then the new ones started.
     */
    default boolean isMultiplexed() { return false; }

}
