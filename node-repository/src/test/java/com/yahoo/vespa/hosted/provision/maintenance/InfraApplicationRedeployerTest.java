package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.IP;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author jonmv
 */
class InfraApplicationRedeployerTest {

    private static final ApplicationId cfghost = InfrastructureApplication.CONFIG_SERVER_HOST.id();
    private static final ApplicationId cfg = InfrastructureApplication.CONFIG_SERVER.id();
    private static final ApplicationId tenanthost = InfrastructureApplication.TENANT_HOST.id();

    @Test
    void testMultiTriggering() throws InterruptedException {
        TestLocks locks = new TestLocks();
        List<Node> nodes = new CopyOnWriteArrayList<>();
        TestInfraDeployer deployer = new TestInfraDeployer();
        InfraApplicationRedeployer redeployer = new InfraApplicationRedeployer(deployer, locks::get, () -> NodeList.copyOf(nodes));
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
        nodes.add(node("host", NodeType.host, State.ready));
        // Re-run for tenanthost clears host from ready, and next run does nothing.
        deployer.expect(tenanthost, () -> {
            nodes.clear();
            return Optional.empty();
        });
        locks.expect(tenanthost, () -> intermezzo::countDown);
        intro.arriveAndAwaitAdvance(); // Let redeployer continue.
        intermezzo.await(10, TimeUnit.SECONDS); // Rendezvous with last, no-op tenanthost redeployment.
        locks.verify();
        deployer.verify();

        // Confighost is triggered again with one ready host. Both applications deploy, and a new trigger redeploys neither.
        locks.expect(cfghost, () -> () -> { });
        locks.expect(cfg, () -> () -> { });
        nodes.add(node("cfghost", NodeType.confighost, State.ready));
        deployer.expect(cfghost, () -> {
            nodes.clear();
            return Optional.empty();
        });
        deployer.expect(cfg, () -> {
            redeployer.readied(NodeType.confighost);
            return Optional.empty();
        });
        locks.expect(cfghost, () -> outro::countDown);
        redeployer.readied(NodeType.confighost);

        outro.await(10, TimeUnit.SECONDS);
        redeployer.close();
        locks.verify();
        deployer.verify();
    }

    @Test
    void testRetries() throws InterruptedException {
        TestLocks locks = new TestLocks();
        List<Node> nodes = new CopyOnWriteArrayList<>();
        TestInfraDeployer deployer = new TestInfraDeployer();
        InfraApplicationRedeployer redeployer = new InfraApplicationRedeployer(deployer, locks::get, () -> NodeList.copyOf(nodes));

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
        nodes.add(node("cfghost", NodeType.confighost, State.ready));
        deployer.expect(cfghost, () -> {
            nodes.set(0, node("cfghost", NodeType.confighost, State.active));
            return Optional.empty();
        });
        deployer.expect(cfg, Optional::empty);
        redeployer.readied(NodeType.confighost);
        latch.await(10, TimeUnit.SECONDS);
        redeployer.close();
        locks.verify();
        deployer.verify();
    }

    private static Node node(String name, NodeType type, State state) {
        return Node.create(name, name, new Flavor(NodeResources.unspecified()), state, type)
                   .ipConfig(IP.Config.of(List.of("1.2.3.4"), List.of("1.2.3.4")))
                   .build();
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
            try { assertEquals(expected.poll(), id); }
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
            assertEquals(List.of(), List.copyOf(expected));
        }

    }

    private static class TestLocks extends Expectations<ApplicationId, Mutex> { }

    private static class TestInfraDeployer extends Expectations<ApplicationId, Optional<Deployment>> implements InfraDeployer {
        @Override public Optional<Deployment> getDeployment(ApplicationId application) { return get(application); }
        @Override public void activateAllSupportedInfraApplications(boolean propagateException) { fail(); }
    }

}
