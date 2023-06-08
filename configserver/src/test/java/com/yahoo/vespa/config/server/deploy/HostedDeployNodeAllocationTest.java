// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.Quota;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.QuotaExceededException;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.session.PrepareParams;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.config.server.deploy.DeployTester.createHostedModelFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HostedDeployNodeAllocationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDeployMultipleVersionsCreatingDifferentHosts() {
        List<ModelFactory> modelFactories = List.of(createHostedModelFactory(Version.fromString("7.2")),
                                                    createHostedModelFactory(Version.fromString("7.3")));
        var provisioner = new VersionProvisioner();
        DeployTester tester = new DeployTester.Builder(temporaryFolder).modelFactories(modelFactories)
                                                                       .provisioner(new MockProvisioner().hostProvisioner(provisioner))
                                                                       .hostedConfigserverConfig(Zone.defaultZone())
                                                                       .build();
        tester.deployApp("src/test/apps/hosted/", "7.3");

        var hosts = containers(tester.getAllocatedHostsOf(tester.applicationId()).getHosts());
        assertEquals("Allocating the superset of hosts of each version", 5, hosts.size());
        assertEquals(resources(3), get("host0", hosts).advertisedResources());
        assertEquals(resources(3), get("host1", hosts).advertisedResources());
        assertEquals(resources(3), get("host2", hosts).advertisedResources());
        assertEquals(resources(2), get("host3", hosts).advertisedResources());
        assertEquals(resources(2), get("host4", hosts).advertisedResources());
    }

    @Test
    public void testExceedsQuota() {
        List<ModelFactory> modelFactories = List.of(createHostedModelFactory(Version.fromString("7.2")),
                                                    createHostedModelFactory(Version.fromString("7.3")));
        var provisioner = new VersionProvisioner();
        DeployTester tester = new DeployTester.Builder(temporaryFolder).modelFactories(modelFactories)
                .provisioner(new MockProvisioner().hostProvisioner(provisioner))
                .hostedConfigserverConfig(Zone.defaultZone())
                .build();

        try {
            tester.deployApp("src/test/apps/hosted/", new PrepareParams.Builder()
                    .vespaVersion("7.3")
                    .quota(new Quota(Optional.of(4), Optional.of(0))));
            fail("Expected to get a QuotaExceededException");
        } catch (QuotaExceededException e) {
            assertEquals("main: The resources used cost $1.02 but your quota is $0.00: Contact support to upgrade your plan.", e.getMessage());
        }
    }

    private HostSpec get(String hostname, Set<HostSpec> hosts) {
        return hosts.stream().filter(host -> host.hostname().equals(hostname)).findAny().orElseThrow();
    }

    private Set<HostSpec> containers(Set<HostSpec> hosts) {
        return hosts.stream().filter(host -> host.membership().get().cluster().type() == ClusterSpec.Type.container).collect(Collectors.toSet());
    }

    private static NodeResources resources(double vcpu) {
        return new NodeResources(vcpu, 1, 1, 1);
    }

    private static class VersionProvisioner implements HostProvisioner {

        int invocation = 0;

        @Override
        public HostSpec allocateHost(String alias) {
            throw new RuntimeException();
        }

        @Override
        public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
            if (cluster.id().value().equals("container")) { // the container cluster from the app package: Use this to test
                if (invocation == 0) { // Building the latest model version, 7.3: Always first
                    invocation++;
                    // Returns nodes which are on both versions to trigger building old versions
                    return List.of(host("host0", resources(3), 0, "7.3", cluster),
                                   host("host1", resources(3), 1, "7.3", cluster),
                                   host("host2", resources(3), 2, "7.2", cluster));
                } else if (invocation == 1) { // Building 7.2
                    invocation++;
                    return List.of(host("host2", resources(2), 2, "7.2", cluster),
                                   host("host3", resources(2), 3, "7.2", cluster),
                                   host("host4", resources(2), 4, "7.2", cluster));
                } else {
                    throw new RuntimeException("Unexpected third invocation");
                }
            }
            else { // for other clusters just return the requested hosts
                List<HostSpec> hosts = new ArrayList<>();
                for (int i = 0; i < capacity.maxResources().nodes(); i++) {
                    hosts.add(host(cluster.id().value() + i,
                                   capacity.maxResources().nodeResources(),
                                   i,
                                   cluster.vespaVersion().toString(),
                                   cluster));
                }
                return hosts;
            }
        }

        private HostSpec host(String hostname, NodeResources resources, int index, String version, ClusterSpec cluster) {
            var membership = ClusterMembership.from(cluster.with(Optional.of(ClusterSpec.Group.from(index))), index);
            return new HostSpec(hostname,
                                resources,
                                resources,
                                resources,
                                membership,
                                Optional.of(Version.fromString(version)),
                                Optional.empty(),
                                Optional.empty());
        }

    }

}
