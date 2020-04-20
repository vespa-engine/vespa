// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.transaction.NestedTransaction;
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

    private final NodeRepositoryProvisioner provisioner;
    private final Map<ApplicationId, ApplicationContext> applications;
    private final Map<ApplicationId, Instant> lastDeployTimes = new HashMap<>();

    /** The number of redeployments done to this */
    public int redeployments = 0;

    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private boolean failActivate = false;

    @Inject
    @SuppressWarnings("unused")
    public MockDeployer() {
        this(null, Clock.systemUTC(), Map.of());
    }

    /**
     * Create a mock deployer which contains a substitute for an application repository, fullfilled to
     * be able to call provision with the right parameters.
     */
    public MockDeployer(NodeRepositoryProvisioner provisioner,
                        Clock clock,
                        Map<ApplicationId, ApplicationContext> applications) {
        this.provisioner = provisioner;
        this.clock = clock;
        this.applications = new HashMap<>(applications);
    }

    public ReentrantLock lock() { return lock; }

    public void setFailActivate(boolean failActivate) { this.failActivate = failActivate; }

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
            return Optional.ofNullable(applications.get(id))
                           .map(application -> new MockDeployment(provisioner, application));
        } finally {
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
        public void activate() {
            if (preparedHosts == null)
                prepare();
            redeployments++;
            if (failActivate)
                throw new IllegalStateException("failActivate is true");
            try (NestedTransaction t = new NestedTransaction()) {
                provisioner.activate(t, application.id(), preparedHosts);
                t.commit();
                lastDeployTimes.put(application.id, clock.instant());
            }
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
