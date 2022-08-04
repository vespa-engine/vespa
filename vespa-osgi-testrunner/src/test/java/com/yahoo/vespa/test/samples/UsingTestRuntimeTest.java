package com.yahoo.vespa.test.samples;

import ai.vespa.cloud.Environment;
import ai.vespa.hosted.cd.TestRuntime;
import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Expect(successful = 1, status = 2)
public class UsingTestRuntimeTest {

    @Test
    void testTestRuntime() {
        TestRuntime runtime = TestRuntime.get();
        assertEquals(Environment.test, runtime.zone().environment());
        assertEquals("name", runtime.zone().region());
        assertNull(runtime.deploymentToTest().endpoint("dummy"));
        assertEquals("1.2.3", runtime.deploymentToTest().platform());
        assertEquals(321, runtime.deploymentToTest().revision());
        assertEquals(1000, runtime.deploymentToTest().deployedAt().toEpochMilli());
    }

}
