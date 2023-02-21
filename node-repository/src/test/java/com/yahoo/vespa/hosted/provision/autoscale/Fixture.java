// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.HostResources;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.BcpGroupInfo;
import com.yahoo.vespa.hosted.provision.autoscale.awsnodes.AwsHostResourcesCalculatorImpl;
import com.yahoo.vespa.hosted.provision.autoscale.awsnodes.AwsNodeTypes;
import com.yahoo.vespa.hosted.provision.provisioning.DynamicProvisioningTester;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Fixture for autoscaling tests.
 *
 * @author bratseth
 */
public class Fixture {

    final DynamicProvisioningTester tester;
    final Zone zone;
    final ApplicationId applicationId;
    final ClusterSpec clusterSpec;
    final Capacity capacity;
    final Loader loader;

    public Fixture(Fixture.Builder builder, Optional<ClusterResources> initialResources, int hostCount) {
        zone = builder.zone;
        applicationId = builder.application;
        clusterSpec = builder.cluster;
        capacity = builder.capacity;
        tester = new DynamicProvisioningTester(builder.zone, builder.resourceCalculator, builder.hostFlavors, builder.flagSource, hostCount);
        var deployCapacity = initialResources.isPresent() ? Capacity.from(initialResources.get()) : capacity;
        tester.deploy(builder.application, builder.cluster, deployCapacity);
        this.loader = new Loader(this);
    }

    public DynamicProvisioningTester tester() { return tester; }

    public ApplicationId applicationId() { return applicationId; }

    public ClusterSpec.Id clusterId() { return clusterSpec.id(); }

    public Application application() {
        return tester().nodeRepository().applications().get(applicationId).orElse(Application.empty(applicationId));
    }

    public AllocatableClusterResources currentResources() {
        return new AllocatableClusterResources(tester.nodeRepository().nodes().list(Node.State.active).owner(applicationId).cluster(clusterId()),
                                               tester.nodeRepository());
    }

    public Cluster cluster() {
        return application().cluster(clusterId()).get();
    }

    public Capacity capacity() { return capacity; }

    public ClusterModel clusterModel() {
        return new ClusterModel(zone,
                                application(),
                                clusterSpec,
                                cluster(),
                                nodes(),
                                tester.nodeRepository().metricsDb(),
                                tester.nodeRepository().clock());
    }

    /** Returns the nodes allocated to the fixture application cluster */
    public NodeList nodes() {
        return tester().nodeRepository().nodes().list(Node.State.active).owner(applicationId).cluster(clusterSpec.id());
    }

    public Loader loader() { return loader; }

    /** Autoscale within the deployed capacity of this. */
    public Autoscaling autoscale() {
        return autoscale(capacity);
    }

    /** Autoscale within the given capacity. */
    public Autoscaling autoscale(Capacity capacity) {
        return tester().autoscale(applicationId, clusterSpec, capacity);
    }

    /** Compute an autoscaling suggestion for this. */
    public Autoscaling suggest() {
        return tester().suggest(applicationId, clusterSpec.id(), capacity.minResources(), capacity.maxResources());
    }

    /** Redeploy with the deployed capacity of this. */
    public void deploy() {
        deploy(capacity);
    }

    /** Redeploy with the given capacity. */
    public void deploy(Capacity capacity) {
        tester().deploy(applicationId, clusterSpec, capacity);
    }

    public void deactivateRetired(Capacity capacity) {
        tester().deactivateRetired(applicationId, clusterSpec, capacity);
    }

    public void setScalingDuration(Duration duration) {
        tester().setScalingDuration(applicationId, clusterSpec.id(), duration);
    }

    public void storeReadShare(double currentReadShare, double maxReadShare) {
        var application = application();
        application = application.with(application.status().withCurrentReadShare(currentReadShare)
                                                  .withMaxReadShare(maxReadShare));
        tester.nodeRepository().applications().put(application, tester.nodeRepository().applications().lock(applicationId));
    }

    public void store(BcpGroupInfo bcpGroupInfo) {
        var application = application();
        application = application.with(application.cluster(clusterId()).get().with(bcpGroupInfo));
        tester.nodeRepository().applications().put(application, tester.nodeRepository().applications().lock(applicationId));
    }

    public static class Builder {

        ApplicationId application = DynamicProvisioningTester.applicationId("application1");
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("cluster1")).vespaVersion("7").build();
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        List<Flavor> hostFlavors = List.of(new Flavor(new NodeResources(100, 100, 100, 1)));
        Optional<ClusterResources> initialResources = Optional.of(new ClusterResources(5, 1, new NodeResources(2, 16, 75, 1)));
        Capacity capacity = Capacity.from(new ClusterResources(2, 1,
                                                               new NodeResources(1, 4, 10, 1, NodeResources.DiskSpeed.any)),
                                          new ClusterResources(20, 1,
                                                               new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any)));
        HostResourcesCalculator resourceCalculator = new DynamicProvisioningTester.MockHostResourcesCalculator(zone);
        final InMemoryFlagSource flagSource = new InMemoryFlagSource();
        int hostCount = 0;

        public Fixture.Builder zone(Zone zone) {
            this.zone = zone;
            return this;
        }

        /** Set to true to behave as if hosts are provisioned dynamically. */
        public Fixture. Builder dynamicProvisioning(boolean dynamicProvisioning) {
            this.zone = new Zone(Cloud.builder()
                                      .dynamicProvisioning(dynamicProvisioning)
                                      .allowHostSharing(zone.cloud().allowHostSharing())
                                      .build(),
                                 zone.system(),
                                 zone.environment(),
                                 zone.region());
            return this;
        }

        /** Set to true to allow multiple nodes be provisioned on the same host. */
        public Fixture. Builder allowHostSharing(boolean allowHostSharing) {
            this.zone = new Zone(Cloud.builder()
                                      .dynamicProvisioning(zone.cloud().dynamicProvisioning())
                                      .allowHostSharing(allowHostSharing)
                                      .build(),
                                 zone.system(),
                                 zone.environment(),
                                 zone.region());
            return this;
        }

        public Fixture.Builder clusterType(ClusterSpec.Type type) {
            cluster = ClusterSpec.request(type, cluster.id()).vespaVersion(cluster.vespaVersion()).build();
            return this;
        }

        public Fixture.Builder cluster(ClusterSpec cluster) {
            this.cluster = cluster;
            return this;
        }

        public Fixture.Builder awsProdSetup(boolean allowHostSharing) {
            return awsSetup(allowHostSharing, Environment.prod);
        }

        public Fixture.Builder awsSetup(boolean allowHostSharing, Environment environment) {
            return this.awsHostFlavors()
                       .awsResourceCalculator()
                       .zone(new Zone(Cloud.builder().dynamicProvisioning(true)
                                           .allowHostSharing(allowHostSharing)
                                           .build(),
                                      SystemName.Public,
                                      environment,
                                      RegionName.from("aws-eu-west-1a")));
        }

        public Fixture.Builder vespaVersion(Version version) {
            cluster = ClusterSpec.request(cluster.type(), cluster.id()).vespaVersion(version).build();
            return this;
        }

        public Fixture.Builder hostFlavors(NodeResources ... hostResources) {
            this.hostFlavors = Arrays.stream(hostResources).map(r -> new Flavor(r)).toList();
            return this;
        }

        /** Adds the host resources available on AWS. */
        public Fixture.Builder awsHostFlavors() {
            this.hostFlavors = AwsNodeTypes.asFlavors();
            return this;
        }

        public Fixture.Builder initialResources(Optional<ClusterResources> initialResources) {
            this.initialResources = initialResources;
            return this;
        }

        public Fixture.Builder capacity(Capacity capacity) {
            this.capacity = capacity;
            return this;
        }

        public Fixture.Builder resourceCalculator(HostResourcesCalculator resourceCalculator) {
            this.resourceCalculator = resourceCalculator;
            return this;
        }

        public Fixture.Builder awsResourceCalculator() {
            this.resourceCalculator = new AwsHostResourcesCalculatorImpl();
            return this;
        }

        public Fixture.Builder hostCount(int hostCount) {
            this.hostCount = hostCount;
            return this;
        }

        public Fixture.Builder hostSharingFlag() {
            var resources = new HostResources(8.0, 32.0, 100.0, 10.0, "fast", "local", null, 6, "x86_64");
            flagSource.withJacksonFlag(PermanentFlags.SHARED_HOST.id(), new SharedHost(List.of(resources)), SharedHost.class);
            return this;
        }

        public Fixture build() {
            return new Fixture(this, initialResources, hostCount);
        }

    }

}
