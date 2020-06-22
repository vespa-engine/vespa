// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.internal;

import ai.vespa.hosted.cd.TestRuntime;
import com.yahoo.component.AbstractComponent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author mortent
 */
public class TestRuntimeProvider extends AbstractComponent {

    private static final AtomicReference<TestRuntime> testRuntime = new AtomicReference<>();

    public TestRuntimeProvider(TestRuntime testRuntime) {
        TestRuntimeProvider.testRuntime.set(testRuntime);
    }

    public static TestRuntime getTestRuntime() {
        return testRuntime.get();
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        testRuntime.set(null);
    }
}
