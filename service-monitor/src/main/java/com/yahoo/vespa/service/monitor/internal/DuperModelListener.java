// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;

/**
 * Interface for listening for changes to the {@link DuperModel}.
 *
 * @author hakon
 */
public interface DuperModelListener {
    /**
     * An application has been activated:
     *
     * <ul>
     *     <li>A synthetic application like the config server application has been added/"activated"
     *     <li>A super model application has been activated (see
     *     {@link com.yahoo.config.model.api.SuperModelListener#applicationActivated(SuperModel, ApplicationInfo)
     *     SuperModelListener}
     * </ul>
     */
    void applicationActivated(ApplicationInfo application);

    /** Application has been removed. */
    void applicationRemoved(ApplicationId id);
}
