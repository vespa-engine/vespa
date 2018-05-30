// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.Container;

/**
 * <p>This interface defines the API for changing the active {@link Container} of a jDISC application. An instance of
 * this class is typically injected into the {@link Application} constructor. If injection is unavailable due to an
 * Application design, an instance of this class is also available as an OSGi service under the full ContainerActivator
 * class name.</p>
 *
 * <p>This interface allows one to create and active a new Container. To do so, one has to 1) call {@link
 * #newContainerBuilder()}, 2) configure the returned {@link ContainerBuilder}, and 3) pass the builder to the {@link
 * #activateContainer(ContainerBuilder)} method.</p>
 *
 * @author Simon Thoresen
 */
public interface ContainerActivator {

    /**
     * This method creates and returns a new {@link ContainerBuilder} object that has the necessary references to the
     * application and its internal components.
     *
     * @return The created builder.
     */
    ContainerBuilder newContainerBuilder();

    /**
     * Creates and activates a {@link Container} based on the provided {@link ContainerBuilder}. By providing a
     * <em>null</em> argument, this method can be used to deactivate the current Container. The returned object can be
     * used to schedule a cleanup task that is executed once the the deactivated Container has terminated.
     *
     * @param builder The builder to activate.
     * @return The previous container, if any.
     * @throws ApplicationNotReadyException If this method is called before {@link Application#start()} or after {@link
     *                                      Application#stop()}.
     */
    DeactivatedContainer activateContainer(ContainerBuilder builder);

}
