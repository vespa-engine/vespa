// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

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

import java.util.Collection;
import java.util.List;

/**
 * @author freva
 */
@SuppressWarnings("unused") // Injected in container from test code (services.xml)
public class MockProvisioner implements Provisioner {

    @Override
    public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
        return List.of();
    }

    @Override
    public void activate(Collection<HostSpec> hosts, ActivationContext context, ApplicationTransaction transaction) { }

    @Override
    public void remove(ApplicationTransaction transaction) { }

    @Override
    public void restart(ApplicationId application, HostFilter filter) { }

    @Override
    public ProvisionLock lock(ApplicationId application) {
        return null;
    }

}
