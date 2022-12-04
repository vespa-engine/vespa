// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import ai.vespa.http.HttpURL.Path;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.orchestrator.restapi.wire.ApplicationReferenceList;
import com.yahoo.vespa.orchestrator.restapi.wire.UrlReference;
import com.yahoo.vespa.service.manager.HealthMonitorApi;
import com.yahoo.vespa.service.monitor.ServiceId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 * @author bjorncs
 */
public class HealthRequestHandler extends RestApiRequestHandler<HealthRequestHandler> {

    private final HealthMonitorApi healthMonitorApi;

    @Inject
    public HealthRequestHandler(ThreadedHttpRequestHandler.Context context,
                                HealthMonitorApi healthMonitorApi) {
        super(context, HealthRequestHandler::createRestApiDefinition);
        this.healthMonitorApi = healthMonitorApi;
    }

    private static RestApi createRestApiDefinition(HealthRequestHandler self) {
        return RestApi.builder()
                .addRoute(RestApi.route("/orchestrator/v1/health")
                        .get(self::getAllInstances))
                .addRoute(RestApi.route("/orchestrator/v1/health/{applicationId}")
                        .get(self::getInstance))
                .registerJacksonResponseEntity(ApplicationReferenceList.class)
                .registerJacksonResponseEntity(ApplicationServices.class)
                .build();
    }

    private ApplicationReferenceList getAllInstances(RestApi.RequestContext context) {
        List<ApplicationId> applications = new ArrayList<>(healthMonitorApi.getMonitoredApplicationIds());
        applications.sort(Comparator.comparing(ApplicationId::serializedForm));

        ApplicationReferenceList list = new ApplicationReferenceList();
        list.applicationList = applications.stream().map(applicationId -> {
            UrlReference reference = new UrlReference();
            reference.url = context.baseRequestURL()
                    .withPath(Path.parse("/orchestrator/v1/health/" + applicationId.serializedForm()))
                    .toString();
            return reference;
        }).collect(Collectors.toList());

        return list;
    }

    private ApplicationServices getInstance(RestApi.RequestContext context) {
        ApplicationId applicationId = ApplicationId.fromSerializedForm(context.pathParameters().getStringOrThrow("applicationId"));

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
