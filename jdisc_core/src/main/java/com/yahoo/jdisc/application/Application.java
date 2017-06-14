// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.core.ApplicationLoader;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;

/**
 * <p>This interface defines the API of the singleton Application that runs in a jDISC instance. An Application instance
 * will always have its {@link #destroy()} method called, regardless of whether {@link #start()} or {@link #stop()}
 * threw any exceptions.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public interface Application {

    /**
     * <p>This method is called by the {@link ApplicationLoader} just after creating this Application instance. Use this
     * method to start the Application's worker thread, and to activate a {@link Container}. If you attempt to call
     * {@link ContainerActivator#activateContainer(ContainerBuilder)} before this method is invoked, that call will
     * throw an {@link ApplicationNotReadyException}. If this method does not throw an exception, the {@link #stop()}
     * method will be called at some time in the future.</p>
     */
    void start();

    /**
     * <p>This method is called by the {@link ApplicationLoader} after the corresponding signal has been issued by the
     * controlling start script. Once this method returns, all calls to {@link
     * ContainerActivator#activateContainer(ContainerBuilder)} will throw {@link ApplicationNotReadyException}s. Use
     * this method to prepare for termination (see {@link #destroy()}).</p>
     */
    void stop();

    /**
     * <p>This method is called by the {@link ApplicationLoader} after first calling {@link #stop()}, and all previous
     * {@link DeactivatedContainer}s have terminated. Use this method to shut down all Application components such as
     * {@link ClientProvider}s and {@link ServerProvider}s.</p>
     */
    void destroy();

}
