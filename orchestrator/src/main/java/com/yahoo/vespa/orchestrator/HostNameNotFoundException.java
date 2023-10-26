// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;

/**
 * Exception thrown if hostname is not found in the system (i.e node repo)
 *
 * @author smorgrav
 */
public class HostNameNotFoundException extends OrchestrationException {

    public HostNameNotFoundException(HostName hostName) {
         super("Hostname " + hostName + " not found in any instances");
    }

}
