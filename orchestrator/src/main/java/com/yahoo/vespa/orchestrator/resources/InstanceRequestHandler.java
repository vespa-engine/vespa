// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.restapi.wire.SlobrokEntryResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.WireHostInfo;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostInfos;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.service.manager.MonitorManager;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.model.ApplicationInstanceGenerator;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.vespa.service.monitor.SlobrokApi;

import java.time.Instant;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getHostsUsedByApplicationInstance;
import static com.yahoo.vespa.orchestrator.OrchestratorUtil.parseApplicationInstanceReference;

/**
 * Provides a read-only API for looking into the current state as seen by the Orchestrator.
 * This API can be unstable and is not meant to be used programmatically.
 *
 * @author andreer
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class InstanceRequestHandler extends RestApiRequestHandler<InstanceRequestHandler> {

    public static final String DEFAULT_SLOBROK_PATTERN = "**";

    private final StatusService statusService;
    private final SlobrokApi slobrokApi;
    private final MonitorManager rootManager;
    private final ServiceMonitor serviceMonitor;
    private final Zone zone;

    @Inject
    public InstanceRequestHandler(ThreadedHttpRequestHandler.Context context,
                                  ServiceMonitor serviceMonitor,
                                  StatusService statusService,
                                  SlobrokApi slobrokApi,
                                  UnionMonitorManager rootManager,
                                  Zone zone) {
        super(context, InstanceRequestHandler::createRestApiDefinition);
        this.statusService = statusService;
        this.slobrokApi = slobrokApi;
        this.rootManager = rootManager;
        this.serviceMonitor = serviceMonitor;
        this.zone = zone;
    }

    private static RestApi createRestApiDefinition(InstanceRequestHandler self) {
        return RestApi.builder()
                .addRoute(RestApi.route("/orchestrator/v1/instances")
                        .get(self::getAllInstances))
                .addRoute(RestApi.route("/orchestrator/v1/instances/{instanceId}")
                        .get(self::getInstance))
                .addRoute(RestApi.route("/orchestrator/v1/instances/{instanceId}/slobrok")
                        .get(self::getSlobrokEntries))
                .addRoute(RestApi.route("/orchestrator/v1/instances/{instanceId}/serviceStatusInfo")
                        .get(self::getServiceStatus))
                .registerJacksonResponseEntity(List.class)
                .registerJacksonResponseEntity(InstanceStatusResponse.class)
                .registerJacksonResponseEntity(ServiceStatusInfo.class)
                // Overriding object mapper to change serialization of timestamps
                .setObjectMapper(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .registerModule(new Jdk8Module())
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true))
                .build();
    }

    private List<ApplicationId> getAllInstances(RestApi.RequestContext context) {
        return serviceMonitor.getAllApplicationInstanceReferences().stream()
                .map(OrchestratorUtil::toApplicationId)
                .sorted()
                .toList();
    }

    private InstanceStatusResponse getInstance(RestApi.RequestContext context) {
        String instanceIdString = context.pathParameters().getStringOrThrow("instanceId");
        ApplicationInstanceReference instanceId = getApplicationInstanceReference(instanceIdString);

        ApplicationInstance applicationInstance
                = serviceMonitor.getApplication(OrchestratorUtil.toApplicationId(instanceId))
                .orElseThrow(RestApiException.NotFound::new);

        HostInfos hostInfos = statusService.getHostInfosByApplicationResolver().apply(applicationInstance.reference());
        TreeMap<HostName, WireHostInfo> hostStatusMap =
                getHostsUsedByApplicationInstance(applicationInstance)
                        .stream()
                        .collect(Collectors.toMap(
                                hostName -> hostName,
                                hostName -> hostInfoToWire(hostInfos.getOrNoRemarks(hostName)),
                                (u, v) -> { throw new IllegalStateException(); },
                                TreeMap::new));
        return InstanceStatusResponse.create(applicationInstance, hostStatusMap);
    }

    // Gets ApplicationInstanceReference when string might be an ApplicationId (tenant:application:instance) or
    // an ApplicationInstanceReference (tenant:application:instance:environment:region:instance).
    // TODO: Accept only strings on the form tenant:application:instance when all users have been
    // updated and return ApplicationId instead of ApplicationInstanceReference.
    private ApplicationInstanceReference getApplicationInstanceReference(String instanceIdString) {
        try {
            ApplicationId applicationId = ApplicationId.fromSerializedForm(instanceIdString);
            return ApplicationInstanceGenerator.toApplicationInstanceReference(applicationId, zone);
        } catch (IllegalArgumentException e) {
            return parseInstanceId(instanceIdString);
        }
    }

    private WireHostInfo hostInfoToWire(HostInfo hostInfo) {
        String hostStatusString = hostInfo.status().asString();
        String suspendedSinceUtcOrNull = hostInfo.suspendedSince().map(Instant::toString).orElse(null);
        return new WireHostInfo(hostStatusString, suspendedSinceUtcOrNull);
    }

    private List<SlobrokEntryResponse> getSlobrokEntries(RestApi.RequestContext context) {
        String instanceId = context.pathParameters().getStringOrThrow("instanceId");
        String pattern = context.queryParameters().getString("pattern").orElse(null);
        ApplicationInstanceReference reference = getApplicationInstanceReference(instanceId);
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(reference);

        if (pattern == null) {
            pattern = DEFAULT_SLOBROK_PATTERN;
        }

        List<Mirror.Entry> entries = slobrokApi.lookup(applicationId, pattern);
        return entries.stream()
                .map(entry -> new SlobrokEntryResponse(entry.getName(), entry.getSpecString()))
                .toList();
    }

    private ServiceStatusInfo getServiceStatus(RestApi.RequestContext context) {
        String instanceId = context.pathParameters().getStringOrThrow("instanceId");
        String clusterIdString = context.queryParameters().getStringOrThrow("clusterId");
        String serviceTypeString = context.queryParameters().getStringOrThrow("serviceType");
        String configIdString = context.queryParameters().getStringOrThrow("configId");
        ApplicationInstanceReference reference = getApplicationInstanceReference(instanceId);
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(reference);

        ClusterId clusterId = new ClusterId(clusterIdString);
        ServiceType serviceType = new ServiceType(serviceTypeString);
        ConfigId configId = new ConfigId(configIdString);

        return rootManager.getStatus(applicationId, clusterId, serviceType, configId);
    }

    private static ApplicationInstanceReference parseInstanceId(String instanceIdString) {
        try {
            return parseApplicationInstanceReference(instanceIdString);
        } catch (IllegalArgumentException e) {
            throw new RestApiException.BadRequest(e.getMessage(), e);
        }
    }

}
