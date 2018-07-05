// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.DeactivatedContainer;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;

/**
 * <p>This interface defines a reference counted resource. This is the parent interface of {@link RequestHandler},
 * {@link ClientProvider} and {@link ServerProvider}, and is used by jDISC to appropriately signal resources as they
 * become candidates for deallocation. As a {@link ContainerBuilder} is {@link
 * ContainerActivator#activateContainer(ContainerBuilder) activated}, all its components are {@link #refer() retained}
 * by that {@link Container}. Once a {@link DeactivatedContainer} terminates, all of that Container's components are
 * {@link ResourceReference#close() released}. This resource tracking allows an Application to implement a significantly
 * simpler scheme for managing its resources than would otherwise be possible.</p>
 *
 * <p>Objects are created with an initial reference count of 1, representing the reference held by the object creator.
 *
 * <p>You should not really think about the management of resources in terms of reference counting, instead think of it
 * in terms of resource ownership. You retain a resource to prevent it from being destroyed while you are using it, and
 * you release a resource once you are done using it.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface SharedResource {

    String SYSTEM_PROPERTY_NAME_DEBUG = "jdisc.debug.resources";
    boolean DEBUG = Boolean.valueOf(System.getProperty(SYSTEM_PROPERTY_NAME_DEBUG));

    /**
     * <p>Increments the reference count of this resource. You call this method to prevent an object from being
     * destroyed until you have finished using it.</p>
     *
     * <p>You MUST keep the returned {@link ResourceReference} object and release the reference by calling
     * {@link ResourceReference#close()} on it. A reference created by this method can NOT be released by calling
     * {@link #release()}.</p>
     *
     * @see ResourceReference#close()
     */
    ResourceReference refer();

    /**
     * <p>Releases the "main" reference to this resource (the implicit reference due to creation of the object).</p>
     *
     * <p>References obtained by calling {@link #refer()} must be released by calling {@link ResourceReference#close()}
     * on the {@link ResourceReference} returned from {@link #refer()}, NOT by calling this method. You call this
     * method once you are done using an object that you have previously caused instantiation of.</p>
     *
     * @see ResourceReference
     */
    void release();

}
