// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.InstanceLookupService;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.restapi.wire.SlobrokEntryResponse;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.service.monitor.SlobrokApi;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static final String DEFAULT_SLOBROK_PATTERN = "**";

    private final StatusService statusService;
    private final SlobrokApi slobrokApi;
    private final InstanceLookupService instanceLookupService;

    @Inject
    public InstanceResource(@Component InstanceLookupService instanceLookupService,
                            @Component StatusService statusService,
                            @Component SlobrokApi slobrokApi) {
        this.instanceLookupService = instanceLookupService;
        this.statusService = statusService;
        this.slobrokApi = slobrokApi;
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
        ApplicationInstanceReference instanceId = parseInstanceId(instanceIdString);

        ApplicationInstance applicationInstance
                = instanceLookupService.findInstanceById(instanceId)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build()));

        Set<HostName> hostsUsedByApplicationInstance = getHostsUsedByApplicationInstance(applicationInstance);
        Map<HostName, HostStatus> hostStatusMap = getHostStatusMap(hostsUsedByApplicationInstance,
                                                                   statusService.forApplicationInstance(instanceId));
        Map<HostName, String> hostStatusStringMap = OrchestratorUtil.mapValues(hostStatusMap, HostStatus::name);
        return InstanceStatusResponse.create(applicationInstance, hostStatusStringMap);
    }

    @GET
    @Path("/{instanceId}/slobrok")
    @Produces(MediaType.APPLICATION_JSON)
    public List<SlobrokEntryResponse> getSlobrokEntries(
            @PathParam("instanceId") String instanceId,
            @QueryParam("pattern") String pattern) {
        ApplicationInstanceReference reference = parseInstanceId(instanceId);
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(reference);

        if (pattern == null) {
            pattern = DEFAULT_SLOBROK_PATTERN;
        }

        List<Mirror.Entry> entries = slobrokApi.lookup(applicationId, pattern);
        return entries.stream()
                .map(entry -> new SlobrokEntryResponse(entry.getName(), entry.getSpec()))
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{instanceId}/serviceStatus")
    @Produces(MediaType.APPLICATION_JSON)
    public ServiceStatus getServiceStatus(
            @PathParam("instanceId") String instanceId,
            @QueryParam("clusterId") String clusterIdString,
            @QueryParam("serviceType") String serviceTypeString,
            @QueryParam("configId") String configIdString) {
        ApplicationInstanceReference reference = parseInstanceId(instanceId);
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(reference);

        if (clusterIdString == null) {
            throwBadRequest("Missing clusterId query parameter");
        }

        if (serviceTypeString == null) {
            throwBadRequest("Missing serviceType query parameter");
        }

        if (configIdString == null) {
            throwBadRequest("Missing configId query parameter");
        }

        ClusterId clusterId = new ClusterId(clusterIdString);
        ServiceType serviceType = new ServiceType(serviceTypeString);
        ConfigId configId = new ConfigId(configIdString);

        return slobrokApi.getStatus(applicationId, clusterId, serviceType, configId);
    }

    static ApplicationInstanceReference parseInstanceId(String instanceIdString) {
        try {
            return parseAppInstanceReference(instanceIdString);
        } catch (IllegalArgumentException e) {
            throwBadRequest(e.getMessage());
            return null;  // Necessary for compiler
        }
    }

    static void throwBadRequest(String message) {
        throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity(message).build());
    }
}
