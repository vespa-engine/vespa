// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import ai.vespa.http.DomainName;
import com.yahoo.component.Version;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.testutils.MockProvisioner;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ControllerApplication;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
 * @author bjorncs
 */
@RunWith(Parameterized.class)
public class InfraDeployerImplTest {

    private static final ApplicationId cfghost = InfrastructureApplication.CONFIG_SERVER_HOST.id();
    private static final ApplicationId cfg = InfrastructureApplication.CONFIG_SERVER.id();
    private static final ApplicationId tenanthost = InfrastructureApplication.TENANT_HOST.id();

    @Parameterized.Parameters(name = "application={0}")
    public static Iterable<Object[]> parameters() {
        return List.of(
                new InfraApplicationApi[]{new ConfigServerApplication()},
                new InfraApplicationApi[]{new ControllerApplication()}
        );
    }

    private final NodeRepositoryTester tester = new NodeRepositoryTester();
    private final NodeRepository nodeRepository = tester.nodeRepository();
    private final Provisioner provisioner = spy(new NodeRepositoryProvisioner(nodeRepository, Zone.defaultZone(), new EmptyProvisionServiceProvider(), new MockMetric()));
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

    @Test
    public void testMultiTriggering() throws InterruptedException {
        TestLocks locks = new TestLocks();
        List<Node> nodes = new CopyOnWriteArrayList<>();
        var duperModel = new TestDuperModelInfraApi();
        var redeployer = new InfraDeployerImpl(
                mock(NodeRepository.class), new MockProvisioner(), duperModel, locks::get,
                () -> NodeList.copyOf(nodes));

        Phaser intro = new Phaser(2);
        CountDownLatch intermezzo = new CountDownLatch(1), outro = new CountDownLatch(1);

        // First run does nothing, as no nodes are ready after all, but several new runs are triggered as this ends.
        locks.expect(tenanthost, () -> () -> { intro.arriveAndAwaitAdvance(); intro.arriveAndAwaitAdvance(); });
        redeployer.readied(NodeType.host);
        intro.arriveAndAwaitAdvance(); // Wait for redeployer to start, before setting up more state.
        // Before re-triggered events from first tenanthost run, we also trigger for confighost, which should then run before those.
        locks.expect(cfghost, () -> () -> { });
        redeployer.readied(NodeType.confighost);
        for (int i = 0; i < 10000; i++) redeployer.readied(NodeType.host);
        nodes.add(node("host", NodeType.host, Node.State.ready));
        // Re-run for tenanthost clears host from ready, and next run does nothing.
        duperModel.expect(tenanthost, () -> {
            nodes.clear();
            return Optional.empty();
        });
        locks.expect(tenanthost, () -> intermezzo::countDown);
        intro.arriveAndAwaitAdvance(); // Let redeployer continue.
        intermezzo.await(10, TimeUnit.SECONDS); // Rendezvous with last, no-op tenanthost redeployment.
        locks.verify();
        duperModel.verify();

        // Confighost is triggered again with one ready host. Both applications deploy, and a new trigger redeploys neither.
        locks.expect(cfghost, () -> () -> { });
        locks.expect(cfg, () -> () -> { });
        nodes.add(node("cfghost", NodeType.confighost, Node.State.ready));
        duperModel.expect(cfghost, () -> {
            nodes.clear();
            return Optional.empty();
        });
        duperModel.expect(cfg, () -> {
            redeployer.readied(NodeType.confighost);
            return Optional.empty();
        });
        locks.expect(cfghost, () -> outro::countDown);
        redeployer.readied(NodeType.confighost);

        outro.await(10, TimeUnit.SECONDS);
        redeployer.close();
        locks.verify();
        duperModel.verify();
    }

    @Test
    public void testRetries() throws InterruptedException {
        TestLocks locks = new TestLocks();
        List<Node> nodes = new CopyOnWriteArrayList<>();
        var duperModel = new TestDuperModelInfraApi();
        var redeployer = new InfraDeployerImpl(
                mock(NodeRepository.class), new MockProvisioner(), duperModel, locks::get,
                () -> NodeList.copyOf(nodes));

        // Does nothing.
        redeployer.readied(NodeType.tenant);

        // Getting lock fails with runtime exception; no deployments, no retries.
        locks.expect(tenanthost, () -> { throw new RuntimeException("Failed"); });
        redeployer.readied(NodeType.host);

        // Getting lock times out for configserver application; deployment of configserverapp is retried, but host is done.
        CountDownLatch latch = new CountDownLatch(1);
        locks.expect(cfghost, () -> () -> { });
        locks.expect(cfg, () -> { throw new UncheckedTimeoutException("Timeout"); });
        locks.expect(cfg, () -> latch::countDown);
        nodes.add(node("cfghost", NodeType.confighost, Node.State.ready));
        duperModel.expect(cfghost, () -> {
            nodes.set(0, node("cfghost", NodeType.confighost, Node.State.active));
            return Optional.empty();
        });
        duperModel.expect(cfg, Optional::empty);
        redeployer.readied(NodeType.confighost);
        latch.await(10, TimeUnit.SECONDS);
        redeployer.close();
        locks.verify();
        duperModel.verify();
    }

    private static Node node(String name, NodeType type, Node.State state) {
        return Node.create(name, name, new Flavor(NodeResources.unspecified()), state, type)
                .ipConfig(IP.Config.of(List.of("1.2.3.4"), List.of("1.2.3.4")))
                .build();
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

    private static class Expectations<T, R> {
        final Queue<T> expected = new ConcurrentLinkedQueue<>();
        final Queue<Throwable> stacks = new ConcurrentLinkedQueue<>();
        final Queue<Supplier<R>> reactions = new ConcurrentLinkedQueue<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        void expect(T id, Supplier<R> reaction) {
            expected.add(id);
            stacks.add(new AssertionError("Failed expectation of " + id));
            reactions.add(reaction);
        }

        R get(T id) {
            Throwable s = stacks.poll();
            if (s == null) s = new AssertionError("Unexpected invocation with " + id);
            try { Assertions.assertEquals(expected.poll(), id); }
            catch (Throwable t) {
                StackTraceElement[] trace = t.getStackTrace();
                t.setStackTrace(s.getStackTrace());
                s.setStackTrace(trace);
                t.addSuppressed(s);
                if ( ! failure.compareAndSet(null, t)) failure.get().addSuppressed(t);
                throw t;
            }
            return reactions.poll().get();
        }

        @SuppressWarnings("unchecked")
        <E extends Throwable> void verify() throws E {
            if (failure.get() != null) throw (E) failure.get();
            Assertions.assertEquals(List.of(), List.copyOf(expected));
        }
    }

    private static class TestLocks extends Expectations<ApplicationId, Mutex> { }
    
    private static class TestDuperModelInfraApi extends Expectations<ApplicationId, Optional<InfraApplicationApi>> implements DuperModelInfraApi {

        @Override  public List<InfraApplicationApi> getSupportedInfraApplications() { throw new AssertionError("Should not be invoked"); }

        @Override
        public Optional<InfraApplicationApi> getInfraApplication(ApplicationId applicationId) {
            return get(applicationId);
        }

        @Override
        public boolean infraApplicationIsActive(ApplicationId applicationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void infraApplicationActivated(ApplicationId applicationId, List<DomainName> hostnames) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void infraApplicationRemoved(ApplicationId applicationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void infraApplicationsIsNowComplete() {
            throw new UnsupportedOperationException();
        }
    }

}
