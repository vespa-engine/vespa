// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.ClusterMetricSnapshot;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class AutoscalingMaintainerTester {

    private final ProvisioningTester provisioningTester;
    private final MetricsDb metricsDb;
    private final AutoscalingMaintainer maintainer;
    private final MockDeployer deployer;

    public AutoscalingMaintainerTester(MockDeployer.ApplicationContext ... appContexts) {
        provisioningTester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east3")))
                                                                    .flavorsConfig(flavorsConfig())
                                                                    .build();
        provisioningTester.clock().setInstant(Instant.ofEpochMilli(0));
        Map<ApplicationId, MockDeployer.ApplicationContext> apps = Arrays.stream(appContexts)
                                                                         .collect(Collectors.toMap(c -> c.id(), c -> c));
        deployer = new MockDeployer(provisioningTester.provisioner(), provisioningTester.clock(), apps);
        metricsDb = MetricsDb.createTestInstance(provisioningTester.nodeRepository());
        maintainer = new AutoscalingMaintainer(provisioningTester.nodeRepository(),
                                               metricsDb,
                                               deployer,
                                               new TestMetric(),
                                               Duration.ofMinutes(1));
        provisioningTester.makeReadyNodes(20, "flt", NodeType.host, 8);
        provisioningTester.activateTenantHosts();
    }

    public NodeRepository nodeRepository() { return provisioningTester.nodeRepository(); }
    public ManualClock clock() { return provisioningTester.clock(); }
    public MockDeployer deployer() { return deployer; }
    public AutoscalingMaintainer maintainer() { return maintainer; }
    public MetricsDb nodeMetricsDb() { return metricsDb; }

    public static ApplicationId makeApplicationId(String name) { return ProvisioningTester.applicationId(name); }
    public static ClusterSpec containerClusterSpec() { return ProvisioningTester.containerClusterSpec(); }

    public List<Node> deploy(ApplicationId application, ClusterSpec cluster, Capacity capacity) {
        return provisioningTester.deploy(application, cluster, capacity);
    }

    public void addMeasurements(float cpu, float mem, float disk, long generation, int count, ApplicationId applicationId) {
        NodeList nodes = nodeRepository().nodes().list(Node.State.active).owner(applicationId);
        for (int i = 0; i < count; i++) {
            for (Node node : nodes)
                metricsDb.addNodeMetrics(List.of(new Pair<>(node.hostname(), new NodeMetricSnapshot(clock().instant(),
                                                                                                    cpu,
                                                                                                    mem,
                                                                                                    disk,
                                                                                                    generation,
                                                                                                    true,
                                                                                                    true,
                                                                                                    0.0))));
        }
    }

    /** Creates the given number of measurements, spaced 5 minutes between, using the given function */
    public void addQueryRateMeasurements(ApplicationId application,
                                         ClusterSpec.Id cluster,
                                         int measurements,
                                         IntFunction<Double> queryRate) {
        Instant time = clock().instant();
        for (int i = 0; i < measurements; i++) {
            metricsDb.addClusterMetrics(application, Map.of(cluster, new ClusterMetricSnapshot(time, queryRate.apply(i), 0.0)));
            time = time.plus(Duration.ofMinutes(5));
        }
    }

    public Cluster cluster(ApplicationId application, ClusterSpec cluster) {
        return nodeRepository().applications().get(application).get().cluster(cluster.id()).get();
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("flt", 30, 30, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("cpu", 40, 20, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("mem", 20, 40, 40, 3, Flavor.Type.BARE_METAL);
        return b.build();
    }

}
