// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class OsUpgradeActivatorTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void activates_upgrade() {
        var osVersions = tester.nodeRepository().osVersions();
        var osUpgradeActivator = new OsUpgradeActivator(tester.nodeRepository(), Duration.ofDays(1), new TestMetric());
        var version0 = Version.fromString("7.0");

        // Create infrastructure nodes
        var configHostApplication = ApplicationId.from("hosted-vespa", "configserver-host", "default");
        var configHostNodes = tester.makeReadyNodes(3, "default", NodeType.confighost, 1);
        tester.prepareAndActivateInfraApplication(configHostApplication, NodeType.confighost, version0);

        var tenantHostApplication = ApplicationId.from("hosted-vespa", "tenant-host", "default");
        var tenantHostNodes = tester.makeReadyNodes(3, "default", NodeType.host, 1);
        tester.prepareAndActivateInfraApplication(tenantHostApplication, NodeType.host, version0);

        var allNodes = new ArrayList<>(configHostNodes);
        allNodes.addAll(tenantHostNodes);

        // All nodes are on initial version
        assertEquals(version0, minWantedVersion(allNodes));
        completeUpgradeOf(configHostNodes);
        completeUpgradeOf(tenantHostNodes);
        assertEquals("All nodes are on initial version", version0, minCurrentVersion(allNodes));

        // New OS target version is set
        var osVersion0 = Version.fromString("8.0");
        osVersions.setTarget(NodeType.host, osVersion0, false);
        osVersions.setTarget(NodeType.confighost, osVersion0, false);

        // New OS version is activated as there is no ongoing Vespa upgrade
        osUpgradeActivator.maintain();
        assertTrue("OS version " + osVersion0 + " is active", osUpgradeActive(NodeType.confighost, NodeType.host));

        // Tenant hosts start upgrading to next Vespa version
        var version1 = Version.fromString("7.1");
        tester.prepareAndActivateInfraApplication(tenantHostApplication, NodeType.host, version1);
        assertEquals("Wanted version of " + NodeType.host + " is raised", version1,
                     minWantedVersion(tenantHostNodes));

        // Activator pauses upgrade for tenant hosts only
        osUpgradeActivator.maintain();
        assertTrue("OS version " + osVersion0 + " is active", osUpgradeActive(NodeType.confighost));
        assertFalse("OS version " + osVersion0 + " is inactive", osUpgradeActive(NodeType.host));

        // One tenant host fails and is no longer considered
        tester.nodeRepository().nodes().fail(tenantHostNodes.get(0).hostname(), Agent.system, this.getClass().getSimpleName());

        // Remaining hosts complete their Vespa upgrade
        var healthyTenantHostNodes = tenantHostNodes.subList(1, tenantHostNodes.size());
        completeUpgradeOf(healthyTenantHostNodes);
        assertEquals("Tenant hosts upgraded", version1, minCurrentVersion(healthyTenantHostNodes));

        // Activator resumes OS upgrade of tenant hosts
        osUpgradeActivator.run();
        assertTrue("OS version " + osVersion0 + " is active", osUpgradeActive(NodeType.confighost, NodeType.host));
    }

    private boolean osUpgradeActive(NodeType first, NodeType... rest) {
        return tester.nodeRepository().nodes().list().nodeType(first, rest).changingOsVersion().size() > 0;
    }

    private void completeUpgradeOf(List<Node> nodes) {
        tester.patchNodes(nodes, (node) -> node.with(node.status().withVespaVersion(node.allocation().get().membership().cluster().vespaVersion())));
    }

    private Stream<Node> streamUpdatedNodes(List<Node> nodes) {
        return tester.nodeRepository().nodes().list().stream().filter(nodes::contains);
    }

    private Version minCurrentVersion(List<Node> nodes) {
        return streamUpdatedNodes(nodes).map(Node::status)
                                        .map(Status::vespaVersion)
                                        .flatMap(Optional::stream)
                                        .min(Comparator.naturalOrder())
                                        .orElse(Version.emptyVersion);
    }

    private Version minWantedVersion(List<Node> nodes) {
        return streamUpdatedNodes(nodes).map(Node::allocation)
                                        .flatMap(Optional::stream)
                                        .map(Allocation::membership)
                                        .map(ClusterMembership::cluster)
                                        .map(ClusterSpec::vespaVersion)
                                        .min(Comparator.naturalOrder())
                                        .orElse(Version.emptyVersion);
    }

}
