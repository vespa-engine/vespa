// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.application.*;
import com.yahoo.jdisc.handler.RequestHandler;

/**
 * <p>This interface defines a component that is capable of acting as a client to an external server. To activate a
 * ClientProvider it must be {@link BindingRepository#bind(String, Object) bound} to a {@link UriPattern} within a
 * {@link ContainerBuilder}, and that builder must be {@link ContainerActivator#activateContainer(ContainerBuilder)
 * activated}.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface ClientProvider extends RequestHandler {

    /**
     * <p>This is a synchronous method to configure this ClientProvider. The {@link Container} does <em>not</em> call
     * this method, instead it is a required step in the {@link Application} initialization code.</p>
     */
    void start();

}
