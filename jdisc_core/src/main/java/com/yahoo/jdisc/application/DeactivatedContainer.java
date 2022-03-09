// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;

/**
 * <p>This interface represents a {@link Container} which has been deactivated. An instance of this class is returned by
 * the {@link ContainerActivator#activateContainer(ContainerBuilder)} method, and is used to schedule a cleanup task
 * that is executed once the the deactivated Container has terminated.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface DeactivatedContainer {

    /**
     * <p>Returns the context object that was previously attached to the corresponding {@link ContainerBuilder} through
     * the {@link ContainerBuilder#setAppContext(Object)} method. This is useful for tracking {@link Application}
     * specific resources that are to be tracked alongside a {@link Container}.</p>
     *
     * @return The Application context.
     */
    Object appContext();

    /**
     * <p>Schedules the given {@link Runnable} to execute once this DeactivatedContainer has terminated. A
     * DeactivatedContainer is considered to have terminated once there are no more {@link Request}s, {@link Response}s
     * or corresponding {@link ContentChannel}s being processed by components that belong to it.</p>
     *
     * <p>If termination has already occurred, this method immediately runs the given Runnable in the current thread.</p>
     *
     * @param task The task to run once this DeactivatedContainer has terminated.
     */
    void notifyTermination(Runnable task);

}
