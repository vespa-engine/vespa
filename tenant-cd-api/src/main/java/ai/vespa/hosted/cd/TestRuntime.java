// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import ai.vespa.cloud.Zone;

import java.util.ServiceLoader;

/**
 * The place to obtain environment-dependent configuration for test of a Vespa deployment.
 *
 * @author jvenstad
 * @author mortent
 */
public interface TestRuntime {
    static TestRuntime get() {
        ServiceLoader<TestRuntime> serviceLoader = ServiceLoader.load(TestRuntime.class);
        return serviceLoader.findFirst().orElseThrow(() -> new RuntimeException("No TestRuntime implementation found"));
    }

    Deployment deploymentToTest();

    Zone zone();

}
