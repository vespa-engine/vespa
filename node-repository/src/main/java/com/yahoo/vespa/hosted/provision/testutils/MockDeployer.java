// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class MockDeployer implements Deployer {

    // For actual deploy mode
    private final NodeRepositoryProvisioner provisioner;
    private final Map<ApplicationId, ApplicationContext> applications;
    // For mock deploy anything, changing wantToRetire to retired only
    private final NodeRepository nodeRepository;

    /** The number of redeployments done to this, which is also the config generation */
    public int redeployments = 0;

    private final Map<ApplicationId, Instant> lastDeployTimes = new HashMap<>();
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private boolean failActivate = false;
    private boolean bootstrapping = true;

    /** Create a mock deployer which returns empty on every deploy request. */
    @Inject
    @SuppressWarnings("unused")
    public MockDeployer() {
        this(null, Clock.systemUTC(), Map.of());
    }

    /**
     * Create a mock deployer which returns a deployment on every request,
     * and fulfills it by not actually deploying but only changing any wantToRetire nodes
     * for the application to retired.
     */
    public MockDeployer(NodeRepository nodeRepository) {
        this.provisioner = null;
        this.applications = Map.of();
        this.nodeRepository = nodeRepository;

        this.clock = nodeRepository.clock();
    }

    /**
     * Create a mock deployer which contains a substitute for an application repository, filled to
     * be able to call provision with the right parameters.
     */
    public MockDeployer(NodeRepositoryProvisioner provisioner,
                        Clock clock,
                        Map<ApplicationId, ApplicationContext> applications) {
        this.provisioner = provisioner;
        this.applications = new HashMap<>(applications);
        this.nodeRepository = null;

        this.clock = clock;
    }

    public ReentrantLock lock() { return lock; }

    public void setFailActivate(boolean failActivate) { this.failActivate = failActivate; }

    public void setBootstrapping(boolean bootstrapping) { this.bootstrapping = bootstrapping; }

    @Override
    public Optional<Deployment> deployFromLocalActive(ApplicationId id, boolean bootstrap) {
        return deployFromLocalActive(id, Duration.ofSeconds(60));
    }

    @Override
    public Optional<Deployment> deployFromLocalActive(ApplicationId id, Duration timeout) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            if (provisioner != null)
                return Optional.ofNullable(applications.get(id))
                                .map(application -> new MockDeployment(provisioner, application));
            else
                return Optional.of(new RetiringOnlyMockDeployment(nodeRepository, id));
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Deployment> deployFromLocalActive(ApplicationId application, Duration timeout, boolean bootstrap) {
        return deployFromLocalActive(application, timeout);
    }

    @Override
    public Optional<Instant> lastDeployTime(ApplicationId application) {
        return Optional.ofNullable(lastDeployTimes.get(application));
    }

    @Override
    public boolean bootstrapping() {
        return bootstrapping;
    }

    @Override
    public Duration serverDeployTimeout() { return Duration.ofSeconds(60); }

    public void removeApplication(ApplicationId applicationId) {
        new MockDeployment(provisioner, new ApplicationContext(applicationId, List.of())).activate();

        applications.remove(applicationId);
        lastDeployTimes.remove(applicationId);
    }

    public class MockDeployment implements Deployment {

        private final NodeRepositoryProvisioner provisioner;
        private final ApplicationContext application;

        /** The list of hosts prepared in this. Only set after prepare is called (and a provisioner is assigned) */
        private List<HostSpec> preparedHosts = null;

        private MockDeployment(NodeRepositoryProvisioner provisioner, ApplicationContext application) {
            this.provisioner = provisioner;
            this.application = application;
        }

        @Override
        public void prepare() {
            preparedHosts = application.prepare(provisioner);
        }

        @Override
        public long activate() {
            if (preparedHosts == null)
                prepare();
            if (failActivate)
                throw new IllegalStateException("failActivate is true");

            redeployments++;
            try (var lock = provisioner.lock(application.id)) {
                try (NestedTransaction t = new NestedTransaction()) {
                    provisioner.activate(preparedHosts, new ActivationContext(redeployments), new ApplicationTransaction(lock, t));
                    t.commit();
                    lastDeployTimes.put(application.id, clock.instant());
                }
            }
            return redeployments;
        }

        @Override
        public void restart(HostFilter filter) {}

    }

    public class RetiringOnlyMockDeployment implements Deployment {

        private final NodeRepository nodeRepository;
        private final ApplicationId applicationId;

        private RetiringOnlyMockDeployment(NodeRepository nodeRepository, ApplicationId applicationId) {
            this.nodeRepository = nodeRepository;
            this.applicationId = applicationId;
        }

        @Override
        public void prepare() { }

        @Override
        public long activate() {
            lastDeployTimes.put(applicationId, clock.instant());

            for (Node node : nodeRepository.nodes().list().owner(applicationId).state(Node.State.active).retiring()) {
                try (NodeMutex lock = nodeRepository.nodes().lockAndGetRequired(node)) {
                    nodeRepository.nodes().write(lock.node().retire(nodeRepository.clock().instant()), lock);
                }
            }
            return redeployments++;
        }

        @Override
        public void restart(HostFilter filter) {}

    }

    /** An application context which substitutes for an application repository */
    public static class ApplicationContext {

        private final ApplicationId id;
        private final List<ClusterContext> clusterContexts;

        public ApplicationContext(ApplicationId id, List<ClusterContext> clusterContexts) {
            this.id = id;
            this.clusterContexts = clusterContexts;
        }

        public ApplicationContext(ApplicationId id, ClusterSpec cluster, Capacity capacity) {
            this(id, List.of(new ClusterContext(id, cluster, capacity)));
        }

        public ApplicationId id() { return id; }

        /** Returns list of cluster specs of this application. */
        public List<ClusterContext> clusterContexts() { return clusterContexts; }

        private List<HostSpec> prepare(NodeRepositoryProvisioner provisioner) {
            return clusterContexts.stream()
                    .map(clusterContext -> clusterContext.prepare(provisioner))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }

    }

    public static class ClusterContext {

        private final ApplicationId id;
        private final ClusterSpec cluster;
        private final Capacity capacity;

        public ClusterContext(ApplicationId id, ClusterSpec cluster, Capacity capacity) {
            this.id = id;
            this.cluster = cluster;
            this.capacity = capacity;
        }

        public ApplicationId id() { return id; }

        public ClusterSpec cluster() { return cluster; }

        private List<HostSpec> prepare(NodeRepositoryProvisioner provisioner) {
            return provisioner.prepare(id, cluster, capacity, null);
        }

    }

}
