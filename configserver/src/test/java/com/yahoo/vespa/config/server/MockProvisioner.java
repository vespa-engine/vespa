// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;

import java.util.Collection;
import java.util.List;

/**
 * @author mpolden
 */
public class MockProvisioner implements Provisioner {

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
    public void activate(Collection<HostSpec> hosts, ActivationContext context, ApplicationTransaction transaction) {
    }

    @Override
    public void remove(ApplicationTransaction transaction) {
    }

    @Override
    public void restart(ApplicationId application, HostFilter filter) {
    }

    @Override
    public ProvisionLock lock(ApplicationId application) {
        return new ProvisionLock(application, () -> {});
    }

}
