// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import ai.vespa.secret.internal.TypedSecretStore;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.maintenance.JobControl;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.CapacityPolicies;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.EndpointsChecker.HealthChecker;
import com.yahoo.config.provision.EndpointsChecker.HealthCheckerProvider;
import com.yahoo.config.provision.Exclusivity;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.NodeRepositoryConfig;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.applications.Applications;
import com.yahoo.vespa.hosted.provision.archive.ArchiveUriManager;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.backup.Snapshots;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancers;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.NodeAcl;
import com.yahoo.vespa.hosted.provision.node.Nodes;
import com.yahoo.vespa.hosted.provision.os.OsVersions;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;
import com.yahoo.vespa.hosted.provision.persistence.DnsNameResolver;
import com.yahoo.vespa.hosted.provision.persistence.JobControlFlags;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.provisioning.ContainerImages;
import com.yahoo.vespa.hosted.provision.provisioning.FirmwareChecks;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceLimits;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionServiceProvider.ProtoHealthChecker;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;

/**
 * The top level singleton in the node repo, providing access to all its state as child objects.
 *
 * @author bratseth
 */
public class NodeRepository extends AbstractComponent implements HealthCheckerProvider {

    private final CuratorDb db;
    private final Clock clock;
    private final Zone zone;
    private final Nodes nodes;
    private final NodeFlavors flavors;
    private final HostResourcesCalculator resourcesCalculator;
    private final NodeResourceLimits nodeResourceLimits;
    private final NameResolver nameResolver;
    private final OsVersions osVersions;
    private final InfrastructureVersions infrastructureVersions;
    private final FirmwareChecks firmwareChecks;
    private final ContainerImages containerImages;
    private final ArchiveUriManager archiveUriManager;
    private final JobControl jobControl;
    private final Applications applications;
    private final LoadBalancers loadBalancers;
    private final FlagSource flagSource;
    private final MetricsDb metricsDb;
    private final Orchestrator orchestrator;
    private final int spareCount;
    private final ProtoHealthChecker healthChecker;
    private final Snapshots snapshots;

    /**
     * Creates a node repository from a zookeeper provider.
     * This will use the system time to make time-sensitive decisions
     */
    @Inject
    public NodeRepository(NodeRepositoryConfig config,
                          NodeFlavors flavors,
                          ProvisionServiceProvider provisionServiceProvider,
                          Curator curator,
                          Zone zone,
                          FlagSource flagSource,
                          MetricsDb metricsDb,
                          Orchestrator orchestrator,
                          TypedSecretStore secretStore) {
        this(flavors,
             provisionServiceProvider,
             curator,
             Clock.systemUTC(),
             zone,
             new DnsNameResolver(),
             DockerImage.fromString(config.containerImage()),
             optionalImage(config.tenantContainerImage()),
             optionalImage(config.tenantGpuContainerImage()),
             flagSource,
             metricsDb,
             orchestrator,
             config.useCuratorClientCache(),
             zone.environment().isProduction() && !zone.cloud().dynamicProvisioning() && !zone.system().isCd() ? 1 : 0,
             secretStore);
    }

    /**
     * Creates a node repository from a zookeeper provider and a clock instance
     * which will be used for time-sensitive decisions.
     */
    public NodeRepository(NodeFlavors flavors,
                          ProvisionServiceProvider provisionServiceProvider,
                          Curator curator,
                          Clock clock,
                          Zone zone,
                          NameResolver nameResolver,
                          DockerImage containerImage,
                          Optional<DockerImage> tenantContainerImage,
                          Optional<DockerImage> tenantGpuContainerImage,
                          FlagSource flagSource,
                          MetricsDb metricsDb,
                          Orchestrator orchestrator,
                          boolean useCuratorClientCache,
                          int spareCount,
                          TypedSecretStore secretStore) {
        if (provisionServiceProvider.getHostProvisioner().isPresent() != zone.cloud().dynamicProvisioning())
            throw new IllegalArgumentException(String.format(
                    "dynamicProvisioning property must be 1-to-1 with availability of HostProvisioner, was: dynamicProvisioning=%s, hostProvisioner=%s",
                    zone.cloud().dynamicProvisioning(), provisionServiceProvider.getHostProvisioner().map(__ -> "present").orElse("empty")));

        this.flagSource = flagSource;
        this.db = new CuratorDb(flavors, curator, clock, useCuratorClientCache, zone.cloud().account());
        this.clock = clock;
        this.zone = zone;
        this.applications = new Applications(db);
        this.snapshots = new Snapshots(this, secretStore, provisionServiceProvider.getSnapshotStore(), zone.cloud().snapshotPrivateKeySecretName());
        this.nodes = new Nodes(db, zone, clock, orchestrator, applications, snapshots, flagSource);
        this.flavors = flavors;
        this.resourcesCalculator = provisionServiceProvider.getHostResourcesCalculator();
        this.nodeResourceLimits = new NodeResourceLimits(this);
        this.nameResolver = nameResolver;
        this.osVersions = new OsVersions(this, provisionServiceProvider.getHostProvisioner());
        this.infrastructureVersions = new InfrastructureVersions(db);
        this.firmwareChecks = new FirmwareChecks(db, clock);
        this.containerImages = new ContainerImages(containerImage, tenantContainerImage, tenantGpuContainerImage);
        this.archiveUriManager = new ArchiveUriManager(db, zone);
        this.jobControl = new JobControl(new JobControlFlags(db, flagSource));
        this.loadBalancers = new LoadBalancers(db);
        this.metricsDb = metricsDb;
        this.orchestrator = orchestrator;
        this.spareCount = spareCount;
        this.healthChecker = provisionServiceProvider.getHealthChecker();
        nodes.rewrite();
    }

    /** Returns the curator database client used by this */
    public CuratorDb database() { return db; }

    /** Returns the nodes of the node repo. */
    public Nodes nodes() { return nodes; }

    /** Returns the name resolver used to resolve hostname and ip addresses */
    public NameResolver nameResolver() { return nameResolver; }

    /** Returns the OS versions to use for nodes in this */
    public OsVersions osVersions() { return osVersions; }

    /** Returns the infrastructure versions to use for nodes in this */
    public InfrastructureVersions infrastructureVersions() { return infrastructureVersions; }

    /** Returns the status of firmware checks for hosts managed by this. */
    public FirmwareChecks firmwareChecks() { return firmwareChecks; }

    /** Returns the container images to use for nodes in this. */
    public ContainerImages containerImages() { return containerImages; }

    /** Returns the archive URIs to use for nodes in this. */
    public ArchiveUriManager archiveUriManager() { return archiveUriManager; }

    /** Returns the status of maintenance jobs managed by this. */
    public JobControl jobControl() { return jobControl; }

    /** Returns this node repo's view of the applications deployed to it */
    public Applications applications() { return applications; }

    /** Returns the load balancers available in this node repo */
    public LoadBalancers loadBalancers() { return loadBalancers; }

    public NodeFlavors flavors() { return flavors; }

    public HostResourcesCalculator resourcesCalculator() { return resourcesCalculator; }

    public NodeResourceLimits nodeResourceLimits() { return nodeResourceLimits; }

    public FlagSource flagSource() { return flagSource; }

    public MetricsDb metricsDb() { return metricsDb; }

    public Orchestrator orchestrator() { return orchestrator; }

    public NodeRepoStats computeStats() { return NodeRepoStats.computeOver(this); }

    /** Returns the time-keeper of this */
    public Clock clock() { return clock; }

    /** Returns the zone of this */
    public Zone zone() { return zone; }

    /** The number of nodes we should ensure has free capacity for node failures whenever possible */
    public int spareCount() { return spareCount; }

    public Exclusivity exclusivity() { return new Exclusivity(zone, PermanentFlags.SHARED_HOST.bindTo(flagSource).value()); }

    public CapacityPolicies capacityPoliciesFor(ApplicationId applicationId) {
        String adminClusterNodeArchitecture = PermanentFlags.ADMIN_CLUSTER_NODE_ARCHITECTURE
                .bindTo(flagSource)
                .with(INSTANCE_ID, applicationId.serializedForm())
                .value();
        double logserverMemory = PermanentFlags.LOGSERVER_NODE_MEMORY
                .bindTo(flagSource)
                .with(INSTANCE_ID, applicationId.serializedForm())
                .value();
        double clusterControllerMemory = PermanentFlags.CLUSTER_CONTROLLER_NODE_MEMORY
                .bindTo(flagSource)
                .with(INSTANCE_ID, applicationId.serializedForm())
                .value();
        var tuning = new CapacityPolicies.Tuning(Architecture.valueOf(adminClusterNodeArchitecture),
                                                 logserverMemory, clusterControllerMemory);
        return new CapacityPolicies(zone, exclusivity(), applicationId, tuning);
    }

    /**
     * Returns ACLs for the children of the given host.
     *
     * @param host node for which to generate ACLs
     * @return the list of node ACLs
     */
    public List<NodeAcl> getChildAcls(Node host) {
        if ( ! host.type().isHost()) throw new IllegalArgumentException("Only hosts have children");
        NodeList allNodes = nodes().list();
        return allNodes.childrenOf(host)
                       .mapToList(childNode -> childNode.acl(allNodes, loadBalancers, zone));
    }

    /** Removes this application: all nodes are set dirty. */
    public void remove(ApplicationTransaction transaction) {
        NodeList applicationNodes = nodes().list().owner(transaction.application());
        db.writeTo(State.dirty,
                   applicationNodes.asList(),
                   Agent.system,
                   Optional.of("Application is removed"),
                   transaction);
        applications.remove(transaction);
    }

    private static Optional<DockerImage> optionalImage(String image) {
        return Optional.of(image).filter(s -> !s.isEmpty()).map(DockerImage::fromString);
    }

    @Override
    public HealthChecker getHealthChecker() {
        return endpoint -> healthChecker.healthy(endpoint,
                                                 loadBalancers.list(endpoint.applicationId())
                                                              .cluster(endpoint.clusterName())
                                                              .first()
                                                              .map(LoadBalancer::idSeed)
                                                              .orElseThrow(() -> new IllegalArgumentException("no load balancer for '" + endpoint + "'")));
    }

    /** Manage backup snapshots for nodes in this */
    public Snapshots snapshots() {
        return snapshots;
    }

}
