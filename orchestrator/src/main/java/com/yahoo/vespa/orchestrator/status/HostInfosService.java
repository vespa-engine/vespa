// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;

/**
 * @author hakonhall
 */
interface HostInfosService {
    HostInfos getHostInfos(ApplicationInstanceReference application);

    /** Returns false if it is known that the operation was a no-op. */
    boolean setHostStatus(ApplicationInstanceReference application, HostName hostName, HostStatus hostStatus);
}
