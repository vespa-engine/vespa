// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import ai.vespa.cloud.ApplicationId;
import ai.vespa.cloud.Zone;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;

import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * The place to obtain environment-dependent configuration for test of a Vespa deployment.
 *
 * @author jvenstad
 * @author mortent
 */
public interface TestRuntime {

    Logger logger = Logger.getLogger(TestRuntime.class.getName());

    static TestRuntime get() {
        TestRuntime provided = TestRuntimeProvider.getTestRuntime();
        if (provided != null) {
            logger.fine("Using test runtime from TestRuntimeProvider");
            return provided;
        }
        logger.fine("Using test runtime from ServiceLoader");
        return ServiceLoader.load(TestRuntime.class, TestRuntime.class.getClassLoader())
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No TestRuntime initialized"));
    }

    Deployment deploymentToTest();

    Zone zone();

    ApplicationId application();

}
