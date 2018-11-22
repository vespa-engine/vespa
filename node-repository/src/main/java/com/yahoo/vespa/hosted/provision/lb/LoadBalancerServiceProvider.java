// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.di.componentgraph.Provider;

import java.util.List;

/**
 * A provider for a {@link LoadBalancerService}. This provides a default instance for cases where a component has not
 * been explicitly configured.
 *
 * @author mpolden
 */
public class LoadBalancerServiceProvider implements Provider<LoadBalancerService> {

    private static final LoadBalancerService instance = new LoadBalancerService() {

        @Override
        public LoadBalancer create(ApplicationId application, ClusterSpec.Id cluster, List<Real> reals) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(LoadBalancerId loadBalancer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Protocol protocol() {
            throw new UnsupportedOperationException();
        }

    };

    @Override
    public LoadBalancerService get() {
        return instance;
    }

    @Override
    public void deconstruct() {}

}
