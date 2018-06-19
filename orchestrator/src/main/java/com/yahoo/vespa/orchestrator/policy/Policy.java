// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;

/**
 * @author oyving
 */
public interface Policy {
    /**
     * Decide whether to grant a request for temporarily suspending the services on all hosts in the group.
     */
    void grantSuspensionRequest(OrchestratorContext context, ApplicationApi applicationApi) throws HostStateChangeDeniedException;

    void releaseSuspensionGrant(OrchestratorContext context, ApplicationApi application) throws HostStateChangeDeniedException;

    /**
     * Give all hosts in a group permission to be removed from the application.
     *
     * @param context
     * @param applicationApi
     */
    void acquirePermissionToRemove(OrchestratorContext context, ApplicationApi applicationApi) throws HostStateChangeDeniedException;

    /**
     * Release an earlier grant for suspension.
     *
     * @throws HostStateChangeDeniedException if the release failed.
     */
    void releaseSuspensionGrant(
            OrchestratorContext context, ApplicationInstance applicationInstance,
            HostName hostName,
            MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException;
}
