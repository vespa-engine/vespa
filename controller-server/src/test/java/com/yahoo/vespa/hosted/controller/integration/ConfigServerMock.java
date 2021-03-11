// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeploymentData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ProtonMetrics;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing.Status;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NotFoundException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ProxyResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.QuotaUsage;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.RestartFilter;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.ServiceView;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private final Set<ZoneId> inactiveZones = new HashSet<>();
    private final Map<String, EndpointStatus> endpoints = new HashMap<>();
    private final NodeRepositoryMock nodeRepository = new NodeRepositoryMock();
    private final Map<DeploymentId, ServiceConvergence> serviceStatus = new HashMap<>();
    private final Set<ApplicationId> disallowConvergenceCheckApplications = new HashSet<>();
    private final Version initialVersion = new Version(6, 1, 0);
    private final DockerImage initialDockerImage = DockerImage.fromString("registry.example.com/vespa/vespa:6.1.0");
    private final Set<DeploymentId> suspendedApplications = new HashSet<>();
    private final Map<ZoneId, Set<LoadBalancer>> loadBalancers = new HashMap<>();
    private final Set<Environment> deferLoadBalancerProvisioning = new HashSet<>();
    private final Map<DeploymentId, List<Log>> warnings = new HashMap<>();
    private final Map<DeploymentId, Set<String>> rotationNames = new HashMap<>();
    private final Map<DeploymentId, List<ClusterMetrics>> clusterMetrics = new HashMap<>();
    private final Map<DeploymentId, TestReport> testReport = new HashMap<>();
    private List<ProtonMetrics> protonMetrics;

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
    public void provision(ZoneId zone, ApplicationId application, ClusterSpec.Id clusterId) {
        var current = new ClusterResources(2, 1, new NodeResources(2,  8, 50, 1, slow, remote));
        Cluster cluster = new Cluster(clusterId,
                                      ClusterSpec.Type.container,
                                      new ClusterResources(2, 1, new NodeResources(1,  4, 20, 1, slow, remote)),
                                      new ClusterResources(2, 1, new NodeResources(4, 16, 90, 1, slow, remote)),
                                      current,
                                      Optional.of(new ClusterResources(2, 1, new NodeResources(3, 8, 50, 1, slow, remote))),
                                      Optional.empty(),
                                      new Cluster.Utilization(0.1, 0.2, 0.3, 0.4, 0.5, 0.6),
                                      List.of(new Cluster.ScalingEvent(new ClusterResources(0, 0, NodeResources.unspecified()),
                                                                       current,
                                                                       Instant.ofEpochMilli(1234))),
                                      "the autoscaling status",
                                      Duration.ofMinutes(6),
                                      0.7,
                                      0.3);
        nodeRepository.putApplication(zone,
                                      new com.yahoo.vespa.hosted.controller.api.integration.configserver.Application(application,
                                                                                                                     List.of(cluster)));

        Node parent = nodeRepository().list(zone, SystemApplication.tenantHost.id()).stream().findAny()
                                      .orElseThrow(() -> new IllegalStateException("No parent hosts in " + zone));
        nodeRepository().putNodes(zone, new Node.Builder().hostname(hostFor(application, zone))
                                                          .state(Node.State.reserved)
                                                          .type(NodeType.tenant)
                                                          .owner(application)
                                                          .parentHostname(parent.hostname())
                                                          .currentVersion(initialVersion)
                                                          .wantedVersion(initialVersion)
                                                          .currentDockerImage(initialDockerImage)
                                                          .wantedDockerImage(initialDockerImage)
                                                          .currentOsVersion(Version.emptyVersion)
                                                          .wantedOsVersion(Version.emptyVersion)
                                                          .resources(new NodeResources(2, 8, 50, 1, slow, remote))
                                                          .serviceState(Node.ServiceState.unorchestrated)
                                                          .flavor("d-2-8-50")
                                                          .clusterId(clusterId.value())
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
                nodeRepository().putNodes(zone, nodes);
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
    public void setVersion(Version version, ApplicationId application, ZoneId zone) {
        setVersion(zone, nodeRepository.list(zone, application), version, false);
    }

    /** Set version for nodeCount number of nodes in application in a given zone */
    public void setVersion(Version version, List<Node> nodes, ZoneId zone) {
        setVersion(zone, nodes, version, false);
    }

    /** Set OS version for an application in a given zone */
    public void setOsVersion(Version version, ApplicationId application, ZoneId zone) {
        setVersion(zone, nodeRepository.list(zone, application), version, true);
    }

    /** Set OS version for an application in a given zone */
    public void setOsVersion(Version version, List<Node> nodes, ZoneId zone) {
        setVersion(zone, nodes, version, true);
    }

    private void setVersion(ZoneId zone, List<Node> nodes, Version version, boolean osVersion) {
        for (var node : nodes) {
            Node newNode;
            if (osVersion) {
                newNode = new Node.Builder(node).currentOsVersion(version).wantedOsVersion(version).build();
            } else {
                newNode = new Node.Builder(node).currentVersion(version).wantedVersion(version).build();
            }
            nodeRepository().putNodes(zone, newNode);
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

    @Override
    public void setSuspension(DeploymentId deployment, boolean suspend) {
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

    public void setProtonMetrics(List<ProtonMetrics> protonMetrics) {
        this.protonMetrics = protonMetrics;
    }

    public void deferLoadBalancerProvisioningIn(Set<Environment> environments) {
        deferLoadBalancerProvisioning.addAll(environments);
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

    private Set<LoadBalancer> getLoadBalancers(ZoneId zone) {
        return loadBalancers.getOrDefault(zone, new LinkedHashSet<>());
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

    @Override
    public TesterCloud.Status getTesterStatus(DeploymentId deployment) {
        return TesterCloud.Status.SUCCESS;
    }

    @Override
    public String startTests(DeploymentId deployment, TesterCloud.Suite suite, byte[] config) {
        return "Tests started";
    }

    @Override
    public List<LogEntry> getTesterLog(DeploymentId deployment, long after) {
        return List.of();
    }

    @Override
    public boolean isTesterReady(DeploymentId deployment) {
        return false;
    }

    @Override
    public Optional<TestReport> getTestReport(DeploymentId deployment) {
        return Optional.ofNullable(testReport.get(deployment));
    }
    public void setTestReport(DeploymentId deploymentId, TestReport report) {
        testReport.put(deploymentId, report);
    }

    /** Add any of given loadBalancers that do not already exist to the load balancers in zone */
    public void putLoadBalancers(ZoneId zone, List<LoadBalancer> loadBalancers) {
        this.loadBalancers.putIfAbsent(zone, new LinkedHashSet<>());
        this.loadBalancers.get(zone).addAll(loadBalancers);
    }

    public void removeLoadBalancers(ApplicationId application, ZoneId zone) {
        getLoadBalancers(zone).removeIf(lb -> lb.application().equals(application));
    }

    @Override
    public PreparedApplication deploy(DeploymentData deployment) {
        lastPrepareVersion = deployment.platform();
        if (prepareException != null) {
            RuntimeException prepareException = this.prepareException;
            this.prepareException = null;
            throw prepareException;
        }
        DeploymentId id = new DeploymentId(deployment.instance(), deployment.zone());

        applications.put(id, new Application(id.applicationId(), lastPrepareVersion, new ApplicationPackage(deployment.applicationPackage())));
        ClusterSpec.Id cluster = ClusterSpec.Id.from("default");

        if (nodeRepository().list(id.zoneId(), id.applicationId()).isEmpty())
            provision(id.zoneId(), id.applicationId(), cluster);

        this.rotationNames.put(
                id,
                deployment.containerEndpoints().stream()
                          .map(ContainerEndpoint::names)
                          .flatMap(Collection::stream)
                          .collect(Collectors.toSet())
        );

        if (!deferLoadBalancerProvisioning.contains(id.zoneId().environment())) {
            putLoadBalancers(id.zoneId(), List.of(new LoadBalancer(UUID.randomUUID().toString(),
                                                                   id.applicationId(),
                                                                   cluster,
                                                                   HostName.from("lb-0--" + id.applicationId().serializedForm() + "--" + id.zoneId().toString()),
                                                                   LoadBalancer.State.active,
                                                                   Optional.of("dns-zone-1"))));
        }

        return () -> {
            Application application = applications.get(id);
            application.activate();
            List<Node> nodes = nodeRepository.list(id.zoneId(), id.applicationId());
            for (Node node : nodes) {
                nodeRepository.putNodes(id.zoneId(), new Node.Builder(node)
                        .state(Node.State.active)
                        .wantedVersion(application.version().get())
                        .build());
            }
            serviceStatus.put(id, new ServiceConvergence(id.applicationId(),
                                                         id.zoneId(),
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
                    : new ConfigChangeActions(List.of(), List.of(), List.of());
            setConfigChangeActions(null);
            prepareResponse.tenant = new TenantId("tenant");
            prepareResponse.log = warnings.getOrDefault(id, Collections.emptyList());
            return prepareResponse;
        };
    }

    @Override
    public void reindex(DeploymentId deployment, List<String> clusterNames, List<String> documentTypes, boolean indexedOnly) { }

    @Override
    public Optional<ApplicationReindexing> getReindexing(DeploymentId deployment) {
        return Optional.of(new ApplicationReindexing(true,
                                                     Map.of("cluster",
                                                            new ApplicationReindexing.Cluster(Map.of("type", 100L),
                                                                                              Map.of("type", new Status(Instant.ofEpochMilli(345),
                                                                                                                        Instant.ofEpochMilli(456),
                                                                                                                        Instant.ofEpochMilli(567),
                                                                                                                        ApplicationReindexing.State.FAILED,
                                                                                                                        "(＃｀д´)ﾉ",
                                                                                                                        0.1))))));


    }


    @Override
    public void disableReindexing(DeploymentId deployment) { }

    @Override
    public void enableReindexing(DeploymentId deployment) { }

    @Override
    public boolean isSuspended(DeploymentId deployment) {
        return suspendedApplications.contains(deployment);
    }

    @Override
    public void restart(DeploymentId deployment, RestartFilter restartFilter) {
        nodeRepository().requestRestart(deployment, restartFilter.getHostName());
    }

    @Override
    public void deactivate(DeploymentId deployment) throws NotFoundException {
        ApplicationId applicationId = deployment.applicationId();
        nodeRepository().removeNodes(deployment.zoneId(),
                                     nodeRepository().list(deployment.zoneId(), applicationId));
        if ( ! applications.containsKey(deployment))
            throw new NotFoundException("No application with id " + applicationId + " exists, cannot deactivate");
        applications.remove(deployment);
        serviceStatus.remove(deployment);
        removeLoadBalancers(deployment.applicationId(), deployment.zoneId());
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
    public List<ClusterMetrics> getDeploymentMetrics(DeploymentId deployment) {
        return Collections.unmodifiableList(clusterMetrics.getOrDefault(deployment, List.of()));
    }

    @Override
    public List<ProtonMetrics> getProtonMetrics(DeploymentId deployment) {
        return this.protonMetrics;
    }

    // Returns a canned example response
    @Override
    public Map<?,?> getServiceApiResponse(DeploymentId deployment, String serviceName, String restPath) {
        Map<String,List<?>> root = new HashMap<>();
        List<Map<?,?>> resources = new ArrayList<>();
        Map<String,String> resource = new HashMap<>();
        resource.put("url", "http://localhost:8080/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/service/filedistributorservice-dud1f4w037qdxdrn0ovxfdtgw/state/v1/config");
        resources.add(resource);
        root.put("resources", resources);
        return root;
    }

    @Override
    public String getClusterControllerStatus(DeploymentId deployment, String node, String subPath) {
        return "<h1>OK</h1>";
    }

    @Override
    public void setGlobalRotationStatus(DeploymentId deployment, String upstreamName, EndpointStatus status) {
        endpoints.put(upstreamName, status);
    }

    @Override
    public void setGlobalRotationStatus(ZoneId zone, boolean in) {
        if (in) {
            inactiveZones.remove(zone);
        } else {
            inactiveZones.add(zone);
        }
    }

    @Override
    public EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String endpoint) {
        EndpointStatus result = new EndpointStatus(EndpointStatus.Status.in, "", "", 1497618757L);
        return endpoints.getOrDefault(endpoint, result);
    }

    @Override
    public boolean getGlobalRotationStatus(ZoneId zone) {
        return !inactiveZones.contains(zone);
    }

    @Override
    public InputStream getLogs(DeploymentId deployment, Map<String, String> queryParameters) {
        return new ByteArrayInputStream(log.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ProxyResponse getApplicationPackageContent(DeploymentId deployment, String path, URI requestUri) {
        return new ProxyResponse("{\"path\":\"" + path + "\"}", "application/json", 200);
    }

    public void setLogStream(String log) {
        this.log = log;
    }

    @Override
    public List<String> getContentClusters(DeploymentId deployment) {
        return Collections.singletonList("music");
    }

    @Override
    public QuotaUsage getQuotaUsage(DeploymentId deploymentId) {
        var q = new QuotaUsage();
        q.rate = 42.42;
        return q;
    }

    @Override
    public String validateSecretStore(DeploymentId deployment, TenantSecretStore tenantSecretStore, String region, String parameterName) {
        return "{\"settings\":{\"name\":\"foo\",\"role\":\"vespa-secretstore-access\",\"awsId\":\"892075328880\",\"externalId\":\"*****\",\"region\":\"us-east-1\"},\"status\":\"ok\"}";
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
