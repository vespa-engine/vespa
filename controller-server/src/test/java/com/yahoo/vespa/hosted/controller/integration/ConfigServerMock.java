// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.Identifier;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NotFoundException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.ServiceView;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;

/**
 * @author mortent
 * @author jonmv
 */
public class ConfigServerMock extends AbstractComponent implements ConfigServer {

    private final Map<DeploymentId, Application> applications = new LinkedHashMap<>();
    private final Map<String, EndpointStatus> endpoints = new HashMap<>();
    private final NodeRepositoryMock nodeRepository = new NodeRepositoryMock();
    private final Map<DeploymentId, ServiceConvergence> serviceStatus = new HashMap<>();
    private final Set<ApplicationId> disallowConvergenceCheckApplications = new HashSet<>();
    private final Version initialVersion = new Version(6, 1, 0);
    private final Set<DeploymentId> suspendedApplications = new HashSet<>();
    private final Map<ZoneId, List<LoadBalancer>> loadBalancers = new HashMap<>();
    private final Map<DeploymentId, List<Log>> warnings = new HashMap<>();
    private final Map<DeploymentId, Set<String>> rotationNames = new HashMap<>();
    private final Map<DeploymentId, List<ClusterMetrics>> clusterMetrics = new HashMap<>();

    private Version lastPrepareVersion = null;
    private RuntimeException prepareException = null;
    private ConfigChangeActions configChangeActions = null;
    private String log = "INFO - All good";

    @Inject
    public ConfigServerMock(ZoneRegistryMock zoneRegistry) {
        bootstrap(zoneRegistry.zones().all().ids(), SystemApplication.all());
    }

    /** Sets the ConfigChangeActions that will be returned on next deployment. */
    public void setConfigChangeActions(ConfigChangeActions configChangeActions) {
        this.configChangeActions = configChangeActions;
    }

    /** Assigns a reserved tenant node to the given deployment, with initial versions. */
    public void provision(ZoneId zone, ApplicationId application) {
        nodeRepository().putByHostname(zone, new Node.Builder().hostname(hostFor(application, zone))
                                                               .state(Node.State.reserved)
                                                               .type(NodeType.tenant)
                                                               .owner(application)
                                                               .currentVersion(initialVersion)
                                                               .wantedVersion(initialVersion)
                                                               .resources(new NodeResources(2, 8, 50, 1, slow, remote))
                                                               .serviceState(Node.ServiceState.unorchestrated)
                                                               .flavor("d-2-8-50")
                                                               .clusterId("cluster")
                                                               .clusterType(Node.ClusterType.container)
                                                               .build());
    }

    public HostName hostFor(ApplicationId application, ZoneId zone) {
        return HostName.from("host-" + application.serializedForm() + "-" + zone.value());
    }

    public void bootstrap(List<ZoneId> zones, SystemApplication... applications) {
        bootstrap(zones, List.of(applications));
    }

    public void bootstrap(List<ZoneId> zones, List<SystemApplication> applications) {
        nodeRepository().clear();
        addNodes(zones, applications);
    }

    public void addNodes(List<ZoneId> zones, List<SystemApplication> applications) {
        for (ZoneId zone : zones) {
            for (SystemApplication application : applications) {
                List<Node> nodes = IntStream.rangeClosed(1, 3)
                                            .mapToObj(i -> new Node.Builder()
                                                    .hostname(HostName.from("node-" + i + "-" + application.id().application()
                                                            .value() + "-" + zone.value()))
                                                    .state(Node.State.active)
                                                    .type(application.nodeType())
                                                    .owner(application.id())
                                                    .currentVersion(initialVersion).wantedVersion(initialVersion)
                                                    .currentOsVersion(Version.emptyVersion).wantedOsVersion(Version.emptyVersion)
                                                    .build())
                                            .collect(Collectors.toList());
                nodeRepository().putByHostname(zone, nodes);
                convergeServices(application.id(), zone);
            }
        }
    }

    /** Converge all services belonging to the given application */
    public void convergeServices(ApplicationId application, ZoneId zone) {
        List<Node> nodes = nodeRepository.list(zone, application);
        serviceStatus.put(new DeploymentId(application, zone), new ServiceConvergence(application,
                                                                                      zone,
                                                                                      true,
                                                                                      2,
                                                                                      nodes.stream()
                                                                                           .map(node -> new ServiceConvergence.Status(node.hostname(),
                                                                                                                                      43,
                                                                                                                                      "container",
                                                                                                                                      2))
                                                                                           .collect(Collectors.toList())));
    }

    /** The version given in the previous prepare call, or empty if no call has been made */
    public Optional<Version> lastPrepareVersion() {
        return Optional.ofNullable(lastPrepareVersion);
    }

    /** The exception to throw on the next prepare run, or null to continue normally */
    public void throwOnNextPrepare(RuntimeException prepareException) {
        this.prepareException = prepareException;
    }

    /** Set version for an application in a given zone */
    public void setVersion(ApplicationId application, ZoneId zone, Version version) {
        setVersion(application, zone, version, -1, false);
    }

    /** Set version for nodeCount number of nodes in application in a given zone */
    public void setVersion(ApplicationId application, ZoneId zone, Version version, int nodeCount) {
        setVersion(application, zone, version, nodeCount, false);
    }

    /** Set OS version for an application in a given zone */
    public void setOsVersion(ApplicationId application, ZoneId zone, Version version) {
        setOsVersion(application, zone, version, -1);
    }

    /** Set OS version for an application in a given zone */
    public void setOsVersion(ApplicationId application, ZoneId zone, Version version, int nodeCount) {
        setVersion(application, zone, version, nodeCount, true);
    }

    private void setVersion(ApplicationId application, ZoneId zone, Version version, int nodeCount, boolean osVersion) {
        int n = 0;
        for (Node node : nodeRepository().list(zone, application)) {
            Node newNode;
            if (osVersion) {
                newNode = new Node.Builder(node).currentOsVersion(version).wantedOsVersion(version).build();
            } else {
                newNode = new Node.Builder(node).currentVersion(version).wantedVersion(version).build();
            }
            nodeRepository().putByHostname(zone, newNode);
            if (++n == nodeCount) break;
        }
    }

    /** The initial version of this config server */
    public Version initialVersion() {
        return initialVersion;
    }

    /** Get deployed application by ID */
    public Optional<Application> application(ApplicationId id, ZoneId zone) {
        return Optional.ofNullable(applications.get(new DeploymentId(id, zone)));
    }

    public void setSuspended(DeploymentId deployment, boolean suspend) {
        if (suspend)
            suspendedApplications.add(deployment);
        else
            suspendedApplications.remove(deployment);
    }

    public void generateWarnings(DeploymentId deployment, int count) {
        List<Log> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Log log = new Log();
            log.time = Instant.now().toEpochMilli();
            log.level = Level.WARNING.getName();
            log.message = "log message " + (count + 1) + " generated by unit test";
            logs.add(log);
        }
        warnings.put(deployment, List.copyOf(logs));
    }

    public Map<DeploymentId, Set<String>> rotationNames() {
        return Collections.unmodifiableMap(rotationNames);
    }

    public void setMetrics(DeploymentId deployment, ClusterMetrics clusterMetrics) {
        setMetrics(deployment, List.of(clusterMetrics));
    }

    public void setMetrics(DeploymentId deployment, List<ClusterMetrics> clusterMetrics) {
        this.clusterMetrics.put(deployment, clusterMetrics);
    }

    @Override
    public NodeRepositoryMock nodeRepository() {
        return nodeRepository;
    }

    @Override
    public Optional<ServiceConvergence> serviceConvergence(DeploymentId deployment, Optional<Version> version) {
        if (disallowConvergenceCheckApplications.contains(deployment.applicationId()))
            throw new IllegalStateException(deployment.applicationId() + " should not ask for service convergence");

        return Optional.ofNullable(serviceStatus.get(deployment));
    }

    public void disallowConvergenceCheck(ApplicationId applicationId) {
        disallowConvergenceCheckApplications.add(applicationId);
    }

    private List<LoadBalancer> getLoadBalancers(ZoneId zone) {
        return loadBalancers.getOrDefault(zone, Collections.emptyList());
    }

    @Override
    public List<LoadBalancer> getLoadBalancers(ApplicationId application, ZoneId zone) {
        return getLoadBalancers(zone).stream()
                                     .filter(lb -> lb.application().equals(application))
                                     .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<FlagData> listFlagData(ZoneId zone) {
        return List.of();
    }

    public void addLoadBalancers(ZoneId zone, List<LoadBalancer> loadBalancers) {
        this.loadBalancers.putIfAbsent(zone, new ArrayList<>());
        this.loadBalancers.get(zone).addAll(loadBalancers);
    }

    public void removeLoadBalancers(ApplicationId application, ZoneId zone) {
        getLoadBalancers(zone).removeIf(lb -> lb.application().equals(application));
    }

    @Override
    public PreparedApplication deploy(DeploymentId deployment, DeployOptions deployOptions,
                                      Set<ContainerEndpoint> containerEndpoints,
                                      ApplicationCertificate applicationCertificate, byte[] content) {
        lastPrepareVersion = deployOptions.vespaVersion.map(Version::fromString).orElse(null);
        if (prepareException != null) {
            RuntimeException prepareException = this.prepareException;
            this.prepareException = null;
            throw prepareException;
        }
        applications.put(deployment, new Application(deployment.applicationId(), lastPrepareVersion, new ApplicationPackage(content)));

        if (nodeRepository().list(deployment.zoneId(), deployment.applicationId()).isEmpty())
            provision(deployment.zoneId(), deployment.applicationId());

        this.rotationNames.put(
                deployment,
                containerEndpoints.stream()
                                  .map(ContainerEndpoint::names)
                                  .flatMap(Collection::stream)
                                  .collect(Collectors.toSet())
        );

        return () -> {
            Application application = applications.get(deployment);
            application.activate();
            List<Node> nodes = nodeRepository.list(deployment.zoneId(), deployment.applicationId());
            for (Node node : nodes) {
                nodeRepository.putByHostname(deployment.zoneId(), new Node.Builder(node)
                        .state(Node.State.active)
                        .wantedVersion(application.version().get())
                        .build());
            }
            serviceStatus.put(deployment, new ServiceConvergence(deployment.applicationId(),
                                                                 deployment.zoneId(),
                                                                 false,
                                                                 2,
                                                                 nodes.stream()
                                                                      .map(node -> new ServiceConvergence.Status(node.hostname(),
                                                                                                                 43,
                                                                                                                 "container",
                                                                                                                 1))
                                                                      .collect(Collectors.toList())));

            PrepareResponse prepareResponse = new PrepareResponse();
            prepareResponse.message = "foo";
            prepareResponse.configChangeActions = configChangeActions != null
                    ? configChangeActions
                    : new ConfigChangeActions(Collections.emptyList(),
                                              Collections.emptyList());
            setConfigChangeActions(null);
            prepareResponse.tenant = new TenantId("tenant");
            prepareResponse.log = warnings.getOrDefault(deployment, Collections.emptyList());
            return prepareResponse;
        };
    }

    @Override
    public boolean isSuspended(DeploymentId deployment) {
        return suspendedApplications.contains(deployment);
    }

    @Override
    public void restart(DeploymentId deployment, Optional<Hostname> hostname) {
        nodeRepository().requestRestart(deployment, hostname.map(Identifier::id).map(HostName::from));
    }

    @Override
    public void deactivate(DeploymentId deployment) throws NotFoundException {
        ApplicationId applicationId = deployment.applicationId();
        nodeRepository().removeByHostname(deployment.zoneId(),
                                          nodeRepository().list(deployment.zoneId(), applicationId));
        if ( ! applications.containsKey(deployment))
            throw new NotFoundException("No application with id " + applicationId + " exists, cannot deactivate");
        applications.remove(deployment);
        serviceStatus.remove(deployment);
    }

    // Returns a canned example response
    @Override
    public ApplicationView getApplicationView(String tenantName, String applicationName, String instanceName,
                                              String environment, String region) {
        String cfgHostname = String.format("https://cfg.%s.%s.test.vip:4443", environment, region);
        String cfgServiceUrlPrefix = String.format("%s/serviceview/v1/tenant/%s/application/%s/environment/%s/region/%s/instance/%s/service",
                                                   cfgHostname, tenantName, applicationName,
                                                   environment, region, instanceName);
        ApplicationView applicationView = new ApplicationView();
        ClusterView cluster = new ClusterView();
        cluster.name = "cluster1";
        cluster.type = "content";
        cluster.url = cfgServiceUrlPrefix + "/container-clustercontroller-6s8slgtps7ry8uh6lx21ejjiv/cluster/v2/cluster1";
        ServiceView service = new ServiceView();
        service.configId = "cluster1/storage/0";
        service.host = "host1";
        service.serviceName = "storagenode";
        service.serviceType = "storagenode";
        service.url = cfgServiceUrlPrefix + "/storagenode-awe3slno6mmq2fye191y324jl/state/v1/";
        cluster.services = new ArrayList<>();
        cluster.services.add(service);
        applicationView.clusters = new ArrayList<>();
        applicationView.clusters.add(cluster);
        return applicationView;
    }

    @Override
    public List<ClusterMetrics> getMetrics(DeploymentId deployment) {
        return Collections.unmodifiableList(clusterMetrics.getOrDefault(deployment, List.of()));
    }

    // Returns a canned example response
    @Override
    public Map<?,?> getServiceApiResponse(String tenantName, String applicationName, String instanceName,
                                          String environment, String region, String serviceName, String restPath) {
        Map<String,List<?>> root = new HashMap<>();
        List<Map<?,?>> resources = new ArrayList<>();
        Map<String,String> resource = new HashMap<>();
        resource.put("url", "http://localhost:8080/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/service/filedistributorservice-dud1f4w037qdxdrn0ovxfdtgw/state/v1/config");
        resources.add(resource);
        root.put("resources", resources);
        return root;
    }

    @Override
    public void setGlobalRotationStatus(DeploymentId deployment, String endpoint, EndpointStatus status) {
        endpoints.put(endpoint, status);
    }

    @Override
    public EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String endpoint) {
        EndpointStatus result = new EndpointStatus(EndpointStatus.Status.in, "", "", 1497618757L);
        return endpoints.getOrDefault(endpoint, result);
    }

    @Override
    public InputStream getLogs(DeploymentId deployment, Map<String, String> queryParameters) {
        return IOUtils.toInputStream(log);
    }

    public void setLogStream(String log) {
        this.log = log;
    }

    @Override
    public List<String> getContentClusters(DeploymentId deployment) {
        return Collections.singletonList("music");
    }

    public static class Application {

        private final ApplicationId id;
        private final Version version;
        private boolean activated;
        private final ApplicationPackage applicationPackage;

        private Application(ApplicationId id, Version version, ApplicationPackage applicationPackage) {
            this.id = id;
            this.version = version;
            this.applicationPackage = applicationPackage;
        }

        public ApplicationId id() {
            return id;
        }

        public Optional<Version> version() {
            return Optional.ofNullable(version);
        }

        public boolean activated() {
            return activated;
        }

        public ApplicationPackage applicationPackage() {
            return applicationPackage;
        }

        private void activate() {
            this.activated = true;
        }

    }

}
