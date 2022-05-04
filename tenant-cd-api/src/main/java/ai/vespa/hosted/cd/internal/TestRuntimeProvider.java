// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.internal;

import ai.vespa.hosted.cd.TestRuntime;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author mortent
 */
public interface TestRuntimeProvider  {

    AtomicReference<TestRuntime> testRuntime = new AtomicReference<>();

    void initialize(byte[] config);

    default void updateReference(TestRuntime testRuntime) {
        TestRuntimeProvider.testRuntime.set(testRuntime);
    }

    static TestRuntime getTestRuntime() {
        return testRuntime.get();
    }

}
