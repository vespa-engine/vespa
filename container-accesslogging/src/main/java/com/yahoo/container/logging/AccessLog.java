// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;


import com.google.inject.Inject;
import com.yahoo.component.provider.ComponentRegistry;

import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Logs to all the configured access logs.
 *
 * @author Tony Vaagenes
 */
public class AccessLog {

    private ComponentRegistry<AccessLogInterface> implementers;

    @Inject
    public AccessLog(ComponentRegistry<AccessLogInterface> implementers) {
        this.implementers = implementers;
    }

    public static AccessLog voidAccessLog() {
        return new AccessLog(new ComponentRegistry<>());
    }

    public void log(AccessLogEntry accessLogEntry) {
        for (AccessLogInterface log: implementers.allComponents()) {
            log.log(accessLogEntry);
        }
    }

}
