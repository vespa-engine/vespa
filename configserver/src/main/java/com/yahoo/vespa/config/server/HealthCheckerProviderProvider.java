package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.EndpointsChecker.HealthCheckerProvider;
import com.yahoo.container.di.componentgraph.Provider;

/**
 * Default stub for container health checker, overridden by node-repository when that is present.
 *
 * @author jonmv
 */
public class HealthCheckerProviderProvider implements Provider<HealthCheckerProvider> {

    @Override
    public HealthCheckerProvider get() { return new HealthCheckerProvider() { }; }

    @Override
    public void deconstruct() { }

}
