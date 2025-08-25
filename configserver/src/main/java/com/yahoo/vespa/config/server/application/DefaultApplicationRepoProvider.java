// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.container.di.componentgraph.Provider;

/**
 * Provides the default repo of applications that can be inherited.
 * This contains internal applications for testing only.
 *
 * @author bratseth
 */
public class DefaultApplicationRepoProvider implements Provider<ApplicationRepo> {

    @Override
    public ApplicationRepo get() {
        return new ApplicationRepo();
    }

    @Override
    public void deconstruct() {}

}
