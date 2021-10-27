// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Set;

/**
 * @author hakonhall
 */
interface HostInfosService {
    HostInfos getHostInfos(ApplicationInstanceReference reference);

    /** Returns false if it is known that the operation was a no-op. */
    boolean setHostStatus(ApplicationInstanceReference reference, HostName hostName, HostStatus hostStatus);

    /** Remove application. */
    void removeApplication(ApplicationInstanceReference reference);

    /** Remove hosts for application. */
    void removeHosts(ApplicationInstanceReference reference, Set<HostName> hostnames);
}
