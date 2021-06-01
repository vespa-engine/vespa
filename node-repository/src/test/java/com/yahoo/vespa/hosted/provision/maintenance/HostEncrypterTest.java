// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class HostEncrypterTest {

    private final ApplicationId infraApplication = ApplicationId.from("hosted-vespa", "infra", "default");
    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();
    private final HostEncrypter encrypter = new HostEncrypter(tester.nodeRepository(), Duration.ofDays(1), new MockMetric());

    @Test
    public void no_hosts_encrypted_with_default_flag_value() {
        provisionHosts(1);
        encrypter.maintain();
        assertEquals(0, tester.nodeRepository().nodes().list().encrypting().size());
    }

    @Test
    public void encrypt_hosts() {
        tester.flagSource().withIntFlag(Flags.MAX_ENCRYPTING_HOSTS.id(), 3);
        Supplier<NodeList> hosts = () -> tester.nodeRepository().nodes().list().nodeType(NodeType.host);

        // Provision hosts and deploy applications
        int hostCount = 5;
        ApplicationId app1 = ApplicationId.from("t1", "a1", "i1");
        ApplicationId app2 = ApplicationId.from("t2", "a2", "i2");
        provisionHosts(hostCount);
        deployApplication(app1);
        deployApplication(app2);

        // Encrypts 1 host per stateful cluster and 1 empty host
        encrypter.maintain();
        NodeList allNodes = tester.nodeRepository().nodes().list();
        List<Node> hostsEncrypting = allNodes.nodeType(NodeType.host)
                                             .encrypting()
                                             .sortedBy(Comparator.comparing(Node::hostname))
                                             .asList();
        List<Optional<ApplicationId>> owners = List.of(Optional.of(app1), Optional.of(app2), Optional.empty());
        assertEquals(owners.size(), hostsEncrypting.size());
        for (int i = 0; i < hostsEncrypting.size(); i++) {
            Optional<ApplicationId> owner = owners.get(i);
            List<Node> retiringChildren = allNodes.childrenOf(hostsEncrypting.get(i)).retiring().encrypting().asList();
            assertEquals(owner.isPresent() ? 1 : 0, retiringChildren.size());
            assertEquals("Encrypting host of " + owner.map(ApplicationId::toString)
                                                      .orElse("no application"),
                         owner,
                         retiringChildren.stream()
                                         .findFirst()
                                         .flatMap(Node::allocation)
                                         .map(Allocation::owner));
        }

        // Replace any retired nodes
        replaceNodes(app1);
        replaceNodes(app2);

        // Complete encryption
        completeEncryptionOf(hostsEncrypting);
        assertEquals(3, hosts.get().encrypted().size());

        // Both applications have moved their nodes to the remaining unencrypted hosts
        allNodes = tester.nodeRepository().nodes().list();
        NodeList unencryptedHosts = allNodes.nodeType(NodeType.host).not().encrypted();
        assertEquals(2, unencryptedHosts.size());
        for (var host : unencryptedHosts) {
            assertEquals(1, allNodes.childrenOf(host).owner(app1).size());
            assertEquals(1, allNodes.childrenOf(host).owner(app2).size());
        }

        // Since both applications now occupy all remaining hosts, we can only upgrade 1 at a time
        for (int i = 0; i < unencryptedHosts.size(); i++) {
            encrypter.maintain();
            hostsEncrypting = hosts.get().encrypting().asList();
            assertEquals(1, hostsEncrypting.size());
            replaceNodes(app1);
            replaceNodes(app2);
            completeEncryptionOf(hostsEncrypting);
        }

        // Resuming encryption has no effect as all hosts are now encrypted
        encrypter.maintain();
        NodeList allHosts = hosts.get();
        assertEquals(0, allHosts.encrypting().size());
        assertEquals(allHosts.size(), allHosts.encrypted().size());
    }

    private void provisionHosts(int hostCount) {
        List<Node> provisionedHosts = tester.makeReadyNodes(hostCount, new NodeResources(48, 128, 2000, 10), NodeType.host, 10);
        // Set OS version supporting encryption
        tester.patchNodes(provisionedHosts, (host) -> host.with(host.status().withOsVersion(host.status().osVersion().withCurrent(Optional.of(Version.fromString("8.0"))))));
        tester.prepareAndActivateInfraApplication(infraApplication, NodeType.host);
    }

    private void completeEncryptionOf(List<Node> nodes) {
        Instant now = tester.clock().instant();
        // Redeploy to park retired hosts
        replaceNodes(infraApplication, (application) -> tester.prepareAndActivateInfraApplication(application, NodeType.host));
        List<Node> patchedNodes = tester.patchNodes(nodes, (node) -> {
            assertSame(Node.State.parked, node.state());
            assertTrue(node + " wants to encrypt", node.reports().getReport(Report.WANT_TO_ENCRYPT_ID).isPresent());
            return node.with(node.reports().withReport(Report.basicReport(Report.DISK_ENCRYPTED_ID,
                                                                          Report.Type.UNSPECIFIED,
                                                                          now,
                                                                          "Host is encrypted")));
        });
        patchedNodes = tester.nodeRepository().nodes().deallocate(patchedNodes, Agent.system, getClass().getSimpleName());
        tester.nodeRepository().nodes().setReady(patchedNodes, Agent.system, getClass().getSimpleName());
        tester.activateTenantHosts();
    }

    private void deployApplication(ApplicationId application) {
        ClusterSpec contentSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content1")).vespaVersion("7").build();
        List<HostSpec> hostSpecs = tester.prepare(application, contentSpec, 2, 1, new NodeResources(4, 8, 100, 0.3));
        tester.activate(application, hostSpecs);
    }

    private void replaceNodes(ApplicationId application) {
        replaceNodes(application, this::deployApplication);
    }

    private void replaceNodes(ApplicationId application, Consumer<ApplicationId> deployer) {
        // Deploy to retire nodes
        deployer.accept(application);
        List<Node> retired = tester.nodeRepository().nodes().list().owner(application).retired().asList();
        assertFalse("At least one node is retired", retired.isEmpty());
        tester.nodeRepository().nodes().setRemovable(application, retired);

        // Redeploy to deactivate removable nodes and allocate new ones
        deployer.accept(application);
        tester.nodeRepository().nodes().list(Node.State.inactive).owner(application)
              .forEach(node -> tester.nodeRepository().nodes().removeRecursively(node, true));
    }

}
