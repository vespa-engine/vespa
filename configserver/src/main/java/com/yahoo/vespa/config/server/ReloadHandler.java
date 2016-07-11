// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.ApplicationSet;

/**
 * Interface representing a reload handler.
 *
 * @author lulf
 * @since 5.1.24
 */
public interface ReloadHandler {

    /**
     * Reload config with the one contained in the application.
     *
     * @param applicationSet The set of applications to set as active.
     */
    void reloadConfig(ApplicationSet applicationSet);

    /**
     * Remove an application and resources related to it.
     *
     * @param applicationId to be removed
     */
    void removeApplication(ApplicationId applicationId);

}
