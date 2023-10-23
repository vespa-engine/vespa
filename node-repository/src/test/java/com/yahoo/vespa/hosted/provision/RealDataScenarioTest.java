// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.component.Version;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ApplicationMutex;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.hosted.provision.maintenance.SwitchRebalancer;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.persistence.ApplicationSerializer;
import com.yahoo.vespa.hosted.provision.persistence.DnsNameResolver;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.persistence.NodeSerializer;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.model.builder.xml.dom.DomConfigPayloadBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

/**
 * Scenario tester with real node-repository data loaded from ZK snapshot file
 *
 * How to use:
 *
 * 1. Copy /opt/vespa/conf/configserver-app/node-flavors.xml from config server to /tmp/node-flavors.xml
 * 2. Copy /opt/vespa/var/zookeeper/version-2/snapshot.XXX from config server to /tmp/snapshot
 * 3. Set capacities and specs according to the wanted scenario
 *
 * @author valerijf
 */
public class RealDataScenarioTest {

    private static final Logger log = Logger.getLogger(RealDataScenarioTest.class.getSimpleName());

    @Ignore
    @Test
    public void test() throws Exception {
        ProvisioningTester tester = tester(SystemName.Public, CloudName.AWS, Environment.prod, CloudAccount.empty, parseFlavors(Path.of("/tmp/node-flavors.xml")));
        initFromZk(tester.nodeRepository(), Path.of("/tmp/snapshot"));

        ApplicationId app = ApplicationId.from("tenant", "app", "default");
        Version version = Version.fromString("8.123.4");

        Capacity[] capacities = new Capacity[]{
                Capacity.from(new ClusterResources(1, 1, NodeResources.unspecified())),
                Capacity.from(new ClusterResources(3, 1, NodeResources.unspecified())),
                Capacity.from(new ClusterResources(4, 1, new NodeResources(8, 16, 100, 0.3, fast, remote))),
                Capacity.from(new ClusterResources(2, 1, new NodeResources(4, 8, 100, 0.3, fast, local)))
        };
        ClusterSpec[] specs = new ClusterSpec[]{
                ClusterSpec.request(ClusterSpec.Type.admin, ClusterSpec.Id.from("logserver")).vespaVersion(version).build(),
                ClusterSpec.request(ClusterSpec.Type.admin, ClusterSpec.Id.from("cluster-controllers")).vespaVersion(version).build(),
                ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("container")).vespaVersion(version).build(),
                ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content")).vespaVersion(version).build()
        };

        deploy(tester, app, specs, capacities);
        tester.nodeRepository().nodes().list().owner(app).cluster(specs[1].id()).forEach(System.out::println);

        // Perform a node move
        tester.clock().advance(Duration.ofHours(1)); // Enough time for deployment to not be considered deployed recently
        List<MockDeployer.ClusterContext> contexts = new ArrayList<>();
        for (int i = 0; i < specs.length; i++) {
            contexts.add(new MockDeployer.ClusterContext(app, specs[i], capacities[i]));
        }
        MockDeployer deployer = new MockDeployer(tester.provisioner(), tester.clock(), List.of(new MockDeployer.ApplicationContext(app, contexts)));
        SwitchRebalancer rebalancer = new SwitchRebalancer(tester.nodeRepository(), Duration.ofDays(1), new MockMetric(), deployer);
        rebalancer.run();
    }

    private void deploy(ProvisioningTester tester, ApplicationId app, ClusterSpec[] specs, Capacity[] capacities) {
        assertEquals("Equal capacity and spec count", capacities.length, specs.length);
        List<HostSpec> hostSpecs = IntStream.range(0, capacities.length)
                                            .mapToObj(i -> tester.provisioner().prepare(app, specs[i], capacities[i], log::log))
                                            .flatMap(Collection::stream)
                                            .toList();
        NestedTransaction transaction = new NestedTransaction();
        tester.provisioner().activate(hostSpecs, new ActivationContext(0), new ApplicationTransaction(new ApplicationMutex(app, () -> {}), transaction));
        transaction.commit();
    }

    private static List<Flavor> parseFlavors(Path path) {
        try {
            var element = XmlHelper.getDocumentBuilder().parse(path.toFile()).getDocumentElement();
            return ConfigPayload.fromBuilder(new DomConfigPayloadBuilder(null).build(element)).toInstance(FlavorsConfig.class, "")
                    .flavor().stream()
                    .map(Flavor::new)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void initFromZk(NodeRepository nodeRepository, Path pathToZkSnapshot) throws Exception {
        NodeSerializer nodeSerializer = new NodeSerializer(nodeRepository.flavors());
        Map<Pattern, BiConsumer<byte[], NestedTransaction>> jsonConsumerByPathPattern = Map.of(
                Pattern.compile(".?/provision/v1/nodes/[a-z0-9.-]+\\.(com|cloud).?"), (json, transaction) -> {
                    Node node = nodeSerializer.fromJson(json);
                    nodeRepository.database().addNodesInState(new LockedNodeList(List.of(node), () -> { }), node.state(), Agent.system, transaction);
                },
                Pattern.compile(".?/provision/v1/applications/[a-z0-9:-]+.?"), (json, transaction) ->
                        nodeRepository.database().writeApplication(ApplicationSerializer.fromJson(json), transaction));

        try (StringsIterator iterator = new StringsIterator(pathToZkSnapshot); NestedTransaction transaction = new NestedTransaction()) {
            while (iterator.hasNext()) {
                String s1 = iterator.next();
                if (!iterator.hasNext()) break;
                for (var entry : jsonConsumerByPathPattern.entrySet()) {
                    if (!entry.getKey().matcher(s1).matches()) continue;
                    String s2 = iterator.next();
                    byte[] json = s2.substring(s2.indexOf("{\""), s2.lastIndexOf('}') + 1).getBytes(UTF_8);
                    entry.getValue().accept(json, transaction);
                    break;
                }
            }
            transaction.commit();
        }
    }

    private static ProvisioningTester tester(SystemName systemName, CloudName cloudName, Environment environment, CloudAccount cloudAccount, List<Flavor> flavors) {
        Cloud cloud = Cloud.builder().name(cloudName).dynamicProvisioning(cloudName != CloudName.YAHOO).account(cloudAccount).build();
        NameResolver nameResolver = cloudName == CloudName.YAHOO ? new DnsNameResolver() : new MockNameResolver().mockAnyLookup();
        ProvisioningTester.Builder builder = new ProvisioningTester.Builder()
                .zone(new Zone(cloud, systemName, environment, RegionName.defaultName()))
                .flavors(flavors)
                .nameResolver(nameResolver)
                .spareCount(environment.isProduction() && !cloud.dynamicProvisioning() && !systemName.isCd() ? 1 : 0);
        if (cloud.dynamicProvisioning())
            builder.hostProvisioner(new MockHostProvisioner(flavors, (MockNameResolver) nameResolver, 0));

        return builder.build();
    }

    /** Extracts sequences longer than 5 printable characters from a binary file, similar to `strings` command */
    private static class StringsIterator implements Iterator<String>, AutoCloseable {
        private final BufferedReader reader;
        private final StringBuilder sb = new StringBuilder(1000);
        private StringsIterator(Path path) throws IOException {
            this.reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), UTF_8));
        }

        @Override
        public boolean hasNext() {
            if (!sb.isEmpty()) return true;
            try {
                for (int r; (r = reader.read()) != -1; ) {
                    if (r < 0x20 || r >= 0x7F) {
                        if (sb.isEmpty()) continue; // Still haven't encountered any real data
                        if (sb.length() > 5) break; // We (probably) found some real data
                        sb.setLength(0); // Probably some random binary data that happened to be a printable character, reset
                    } else sb.append((char) r);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return !sb.isEmpty();
        }

        @Override
        public String next() {
            String next = sb.toString();
            sb.setLength(0);
            return next;
        }

        @Override
        public void close() throws Exception {
            reader.close();
        }
    }

}
