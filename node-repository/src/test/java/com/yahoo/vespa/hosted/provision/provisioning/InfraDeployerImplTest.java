// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import ai.vespa.http.DomainName;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ControllerApplication;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
@RunWith(Parameterized.class)
public class InfraDeployerImplTest {

    @Parameterized.Parameters(name = "application={0}")
    public static Iterable<Object[]> parameters() {
        return List.of(
                new InfraApplicationApi[]{new ConfigServerApplication()},
                new InfraApplicationApi[]{new ControllerApplication()}
        );
    }

    private final NodeRepositoryTester tester = new NodeRepositoryTester();
    private final NodeRepository nodeRepository = tester.nodeRepository();
    private final Provisioner provisioner = spy(new NodeRepositoryProvisioner(nodeRepository, Zone.defaultZone(), new EmptyProvisionServiceProvider()));
    private final InfrastructureVersions infrastructureVersions = nodeRepository.infrastructureVersions();
    private final DuperModelInfraApi duperModelInfraApi = mock(DuperModelInfraApi.class);
    private final InfraDeployerImpl infraDeployer;

    private final Version target = Version.fromString("6.123.456");
    private final Version oldVersion = Version.fromString("6.122.333");

    private final InfraApplicationApi application;
    private final NodeType nodeType;

    public InfraDeployerImplTest(InfraApplicationApi application) {
        when(duperModelInfraApi.getInfraApplication(eq(application.getApplicationId()))).thenReturn(Optional.of(application));
        this.application = application;
        this.nodeType = application.getCapacity().type();
        this.infraDeployer = new InfraDeployerImpl(nodeRepository, provisioner, duperModelInfraApi);
    }

    @Test
    public void remove_application_if_without_nodes() {
        remove_application_without_nodes(true);
    }

    @Test
    public void skip_remove_unless_active() {
        remove_application_without_nodes(false);
    }

    private void remove_application_without_nodes(boolean applicationIsActive) {
        infrastructureVersions.setTargetVersion(nodeType, target, false);
        addNode(1, Node.State.failed, Optional.of(target));
        addNode(2, Node.State.parked, Optional.empty());
        when(duperModelInfraApi.infraApplicationIsActive(eq(application.getApplicationId()))).thenReturn(applicationIsActive);

        infraDeployer.getDeployment(application.getApplicationId()).orElseThrow().activate();

        verify(duperModelInfraApi, never()).infraApplicationActivated(any(), any());
        if (applicationIsActive) {
            verify(duperModelInfraApi).infraApplicationRemoved(application.getApplicationId());
            ArgumentMatcher<ApplicationTransaction> txMatcher = tx -> {
                assertEquals(application.getApplicationId(), tx.application());
                return true;
            };
            verify(provisioner).remove(argThat(txMatcher));
            verify(duperModelInfraApi).infraApplicationRemoved(eq(application.getApplicationId()));
        } else {
            verify(provisioner, never()).remove(any());
            verify(duperModelInfraApi, never()).infraApplicationRemoved(any());
        }
    }

    @Test
    public void activate() {
        infrastructureVersions.setTargetVersion(nodeType, target, false);

        addNode(1, Node.State.failed, Optional.of(oldVersion));
        addNode(2, Node.State.parked, Optional.of(target));
        addNode(3, Node.State.active, Optional.of(target));
        addNode(4, Node.State.inactive, Optional.of(target));
        addNode(5, Node.State.dirty, Optional.empty());
        addNode(6, Node.State.ready, Optional.empty());
        Node node7 = addNode(7, Node.State.active, Optional.of(target));
        nodeRepository.nodes().setRemovable(NodeList.of(node7), false);

        infraDeployer.getDeployment(application.getApplicationId()).orElseThrow().activate();

        verify(duperModelInfraApi, never()).infraApplicationRemoved(any());
        verifyActivated("node-3", "node-6");
    }

    @SuppressWarnings("unchecked")
    private void verifyActivated(String... hostnames) {
        verify(duperModelInfraApi).infraApplicationActivated(
                eq(application.getApplicationId()), eq(Stream.of(hostnames).map(DomainName::of).toList()));
        ArgumentMatcher<ApplicationTransaction> transactionMatcher = t -> {
            assertEquals(application.getApplicationId(), t.application());
            return true;
        };
        ArgumentMatcher<Collection<HostSpec>> hostsMatcher = hostSpecs -> {
            assertEquals(Set.of(hostnames), hostSpecs.stream().map(HostSpec::hostname).collect(Collectors.toSet()));
            return true;
        };
        verify(provisioner).activate(argThat(hostsMatcher), any(), argThat(transactionMatcher));
    }

    private Node addNode(int id, Node.State state, Optional<Version> wantedVespaVersion) {
        Node node = tester.addHost("id-" + id, "node-" + id, "default", nodeType);
        Optional<Node> nodeWithAllocation = wantedVespaVersion.map(version -> {
            ClusterSpec clusterSpec = application.getClusterSpecWithVersion(version).with(Optional.of(ClusterSpec.Group.from(0)));
            ClusterMembership membership = ClusterMembership.from(clusterSpec, 0);
            Allocation allocation = new Allocation(application.getApplicationId(), membership, node.resources(), Generation.initial(), false);
            return node.with(allocation);
        });
        return nodeRepository.database().writeTo(state, nodeWithAllocation.orElse(node), Agent.system, Optional.empty());
    }

}
