// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import ai.vespa.cloud.Zone;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import org.osgi.framework.BundleReference;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * The place to obtain environment-dependent configuration for test of a Vespa deployment.
 *
 * @author jvenstad
 * @author mortent
 */
public interface TestRuntime {
    static final Logger logger = Logger.getLogger(TestRuntime.class.getName());
    static TestRuntime get() {
        var classloader = TestRuntime.class.getClassLoader();

        if (classloader instanceof BundleReference) {
            logger.info("Loading Test runtime from TestRuntimeProvider");
            return Optional.ofNullable(TestRuntimeProvider.getTestRuntime())
                    .orElseThrow(() -> new RuntimeException("Component graph not ready, retrying"));
        } else {
            logger.info("Loading Test runtime from ServiceLoader");
            ServiceLoader<TestRuntime> serviceLoader = ServiceLoader.load(TestRuntime.class, TestRuntime.class.getClassLoader());
            return serviceLoader.findFirst().orElseThrow(() -> new RuntimeException("No TestRuntime implementation found"));
        }
    }

    Deployment deploymentToTest();

    Zone zone();

}
