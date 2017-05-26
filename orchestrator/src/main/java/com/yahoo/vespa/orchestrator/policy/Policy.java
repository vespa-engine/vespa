// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

/**
 * @author oyving
 */
public interface Policy {

    /**
     * Decide whether to grant a request for temporarily suspending the services on a host.
     *
     * @throws HostStateChangeDeniedException if the grant was not given.
     */
    void grantSuspensionRequest(
            ApplicationInstance<ServiceMonitorStatus> applicationInstance,
            HostName hostName,
            MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException;

    /**
     * Decide whether to grant a request for temporarily suspending the services on all hosts in the group.
     */
    void grantSuspensionRequest(ApplicationApi applicationApi) throws HostStateChangeDeniedException;

    void releaseSuspensionGrant(ApplicationApi application) throws HostStateChangeDeniedException;

    /**
     * Give all hosts in a group permission to be removed from the application.
     *
     * @param applicationApi
     */
    void acquirePermissionToRemove(ApplicationApi applicationApi) throws HostStateChangeDeniedException;

    /**
     * Release an earlier grant for suspension.
     *
     * @throws HostStateChangeDeniedException if the release failed.
     */
    void releaseSuspensionGrant(
            ApplicationInstance<ServiceMonitorStatus> applicationInstance,
            HostName hostName,
            MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException;
}
