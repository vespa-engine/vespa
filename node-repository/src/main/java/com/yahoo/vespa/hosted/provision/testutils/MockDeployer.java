// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author bratseth
 */
public class MockDeployer implements Deployer {

    private final NodeRepositoryProvisioner provisioner;
    private final Map<ApplicationId, ApplicationContext> applications;

    /** The number of redeployments done to this */
    public int redeployments = 0;

    @Inject
    @SuppressWarnings("unused")
    public MockDeployer() {
        this(null, Collections.emptyMap());
    }

    /**
     * Create a mock deployer which contains a substitute for an application repository, fullfilled to
     * be able to call provision with the right parameters.
     */
    public MockDeployer(NodeRepositoryProvisioner provisioner, Map<ApplicationId, ApplicationContext> applications) {
        this.provisioner = provisioner;
        this.applications = applications;
    }

    @Override
    public Optional<Deployment> deployFromLocalActive(ApplicationId id, Duration timeout) {
        return Optional.of(new MockDeployment(provisioner, applications.get(id)));
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
            try (NestedTransaction t = new NestedTransaction()) {
                provisioner.activate(t, application.id(), preparedHosts);
                t.commit();
            }
        }

        @Override
        public void restart(HostFilter filter) {}

    }

    /** An application context which substitutes for an application repository */
    public static class ApplicationContext {

        private final ApplicationId id;
        private final ClusterSpec cluster;
        private final Capacity capacity;
        private final int groups;

        public ApplicationContext(ApplicationId id, ClusterSpec cluster, Capacity capacity, int groups) {
            this.id = id;
            this.cluster = cluster;
            this.capacity = capacity;
            this.groups = groups;
        }

        public ApplicationId id() { return id; }

        /** Returns the spec of the cluster of this application. Only a single cluster per application is supported */
        public ClusterSpec cluster() { return cluster; }

        private List<HostSpec> prepare(NodeRepositoryProvisioner provisioner) {
            return provisioner.prepare(id, cluster, capacity, groups, null);
        }

    }

}
