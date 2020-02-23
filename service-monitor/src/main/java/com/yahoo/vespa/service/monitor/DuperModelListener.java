// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.service.duper.DuperModel;

/**
 * Interface for listening for changes to the {@link DuperModel}.
 *
 * @author hakonhall
 */
public interface DuperModelListener {
    /**
     * An application has been activated:
     *
     * <ul>
     *     <li>A synthetic application like the config server application has been added/activated
     *     <li>A super model application has been activated (see
     *     {@link com.yahoo.config.model.api.SuperModelListener#applicationActivated(SuperModel, ApplicationInfo)
     *     SuperModelListener}
     * </ul>
     *
     * <p>No other threads will concurrently call any methods on this interface.</p>
     */
    void applicationActivated(ApplicationInfo application);

    /**
     * Application has been removed.
     *
     * <p>No other threads will concurrently call any methods on this interface.</p>
     */
    void applicationRemoved(ApplicationId id);

    /**
     * During bootstrap of the config server, a number of applications are activated before
     * resuming normal operations: The normal "tenant" application (making the super model) and
     * the relevant infrastructure applications. Once all of these have been activated, this method
     * will be invoked.
     *
     * <p>No other threads will concurrently call any methods on this interface.</p>
     */
    void bootstrapComplete();
}
