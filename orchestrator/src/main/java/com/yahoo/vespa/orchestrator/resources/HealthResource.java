// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.orchestrator.restapi.wire.ApplicationReferenceList;
import com.yahoo.vespa.orchestrator.restapi.wire.UrlReference;
import com.yahoo.vespa.service.manager.HealthMonitorApi;
import com.yahoo.vespa.service.monitor.ServiceId;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 */
@Path("/v1/health")
public class HealthResource {
    private final UriInfo uriInfo;
    private final HealthMonitorApi healthMonitorApi;

    @Inject
    public HealthResource(@Context UriInfo uriInfo, @Component HealthMonitorApi healthMonitorApi) {
        this.uriInfo = uriInfo;
        this.healthMonitorApi = healthMonitorApi;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ApplicationReferenceList getAllInstances() {
        List<ApplicationId> applications = new ArrayList<>(healthMonitorApi.getMonitoredApplicationIds());
        applications.sort(Comparator.comparing(ApplicationId::serializedForm));

        ApplicationReferenceList list = new ApplicationReferenceList();
        list.applicationList = applications.stream().map(applicationId -> {
            UrlReference reference = new UrlReference();
            reference.url = uriInfo.getBaseUriBuilder()
                    .path(HealthResource.class)
                    .path(applicationId.serializedForm())
                    .build()
                    .toString();
            return reference;
        }).collect(Collectors.toList());

        return list;
    }

    @GET
    @Path("/{applicationId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ApplicationServices getInstance(@PathParam("applicationId") String applicationIdString) {
        ApplicationId applicationId = ApplicationId.fromSerializedForm(applicationIdString);

        Map<ServiceId, ServiceStatusInfo> services = healthMonitorApi.getServices(applicationId);

        List<ServiceResource> serviceResources = services.entrySet().stream().map(entry -> {
            ServiceResource serviceResource = new ServiceResource();
            serviceResource.clusterId = entry.getKey().getClusterId();
            serviceResource.serviceType = entry.getKey().getServiceType();
            serviceResource.configId = entry.getKey().getConfigId();
            serviceResource.serviceStatusInfo = entry.getValue();
            return serviceResource;
        })
                .sorted(Comparator.comparing(resource -> resource.serviceType.s()))
                .collect(Collectors.toList());

        ApplicationServices applicationServices = new ApplicationServices();
        applicationServices.services = serviceResources;
        return applicationServices;
    }

}
