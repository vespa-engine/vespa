// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.vespa.orchestrator.InstanceLookupService;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.Set;

import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getHostStatusMap;
import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getHostsUsedByApplicationInstance;
import static com.yahoo.vespa.orchestrator.OrchestratorUtil.parseAppInstanceReference;

/**
 * Provides a read-only API for looking into the current state as seen by the Orchestrator.
 * This API can be unstable and is not meant to be used programmatically.
 *
 * @author andreer
 * @author bakksjo
 */
@Path("/v1/instances")
public class InstanceResource {

    private final StatusService statusService;
    private final InstanceLookupService instanceLookupService;

    @Inject
    public InstanceResource(@Component InstanceLookupService instanceLookupService,
                            @Component StatusService statusService) {
        this.instanceLookupService = instanceLookupService;
        this.statusService = statusService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Set<ApplicationInstanceReference> getAllInstances() {
        return instanceLookupService.knownInstances();
    }

    @GET
    @Path("/{instanceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public InstanceStatusResponse getInstance(@PathParam("instanceId") String instanceIdString) {
        ApplicationInstanceReference instanceId;
        try {
            instanceId = parseAppInstanceReference(instanceIdString);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        ApplicationInstance<ServiceMonitorStatus> applicationInstance
                = instanceLookupService.findInstanceById(instanceId)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build()));

        Set<HostName> hostsUsedByApplicationInstance = getHostsUsedByApplicationInstance(applicationInstance);
        Map<HostName, HostStatus> hostStatusMap = getHostStatusMap(hostsUsedByApplicationInstance,
                                                                   statusService.forApplicationInstance(instanceId));
        Map<HostName, String> hostStatusStringMap = OrchestratorUtil.mapValues(hostStatusMap, HostStatus::name);
        return InstanceStatusResponse.create(applicationInstance, hostStatusStringMap);
    }
}
