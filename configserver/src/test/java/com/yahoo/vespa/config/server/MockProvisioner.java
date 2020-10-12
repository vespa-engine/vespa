// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;
import com.yahoo.transaction.NestedTransaction;

import java.util.Collection;
import java.util.List;

/**
 * @author mpolden
 */
public class MockProvisioner implements Provisioner {

    private boolean activated = false;
    private boolean removed = false;
    private boolean restarted = false;
    private ApplicationId lastApplicationId;
    private Collection<HostSpec> lastHosts;
    private HostFilter lastRestartFilter;

    private boolean transientFailureOnPrepare = false;
    private HostProvisioner hostProvisioner = null;

    public MockProvisioner hostProvisioner(HostProvisioner hostProvisioner) {
        this.hostProvisioner = hostProvisioner;
        return this;
    }

    public MockProvisioner transientFailureOnPrepare() {
        transientFailureOnPrepare = true;
        return this;
    }

    @Override
    public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        if (hostProvisioner != null) {
            return hostProvisioner.prepare(cluster, capacity, logger);
        }
        if (transientFailureOnPrepare) {
            throw new LoadBalancerServiceException("Unable to create load balancer", new Exception("some internal exception"));
        }
        throw new UnsupportedOperationException("This mock does not support prepare");
    }

    @Override
    public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) {
        activate(transaction, hosts, lock(application));
    }

    @Override
    public void activate(NestedTransaction transaction, Collection<HostSpec> hosts, ProvisionLock lock) {
        activated = true;
        lastApplicationId = lock.application();
        lastHosts = hosts;
    }

    @Override
    public void remove(NestedTransaction transaction, ApplicationId application) {
        remove(transaction, lock(application));
    }

    @Override
    public void remove(NestedTransaction transaction, ProvisionLock lock) {
        removed = true;
        lastApplicationId = lock.application();
    }

    @Override
    public void restart(ApplicationId application, HostFilter filter) {
        restarted = true;
        lastApplicationId = application;
        lastRestartFilter = filter;
    }

    @Override
    public ProvisionLock lock(ApplicationId application) {
        return new ProvisionLock(application, () -> {});
    }

    public Collection<HostSpec> lastHosts() {
        return lastHosts;
    }

    public boolean activated() {
        return activated;
    }

    public boolean removed() {
        return removed;
    }

    public boolean restarted() {
        return restarted;
    }

    public ApplicationId lastApplicationId() {
        return lastApplicationId;
    }

    public HostFilter lastRestartFilter() {
        return lastRestartFilter;
    }
}
