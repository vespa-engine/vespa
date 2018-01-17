// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.Main;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HardwareBenchmarkerTest {

    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/";
    private static final String VALID_DISK_BENCHMARK_PATH = RESOURCE_PATH + "diskBenchmarkValidOutput";
    private static final String VALID_SLOW_DISK_BENCHMARK_PATH = RESOURCE_PATH + "diskBenchmarkValidSlowOutput";
    private static final String VALID_CPU_BENCHMARK_PATH = RESOURCE_PATH + "cpuCyclesWithCommasTimeWithDotTest.txt";
    private static final String VALID_MEMORY_WRITE_BENCHMARK_PATH = RESOURCE_PATH + "validMemoryWriteSpeed";
    private static final String VALID_MEMORY_READ_BENCHMARK_PATH = RESOURCE_PATH + "validMemoryReadSpeed";
    private final MockCommandExecutor commandExecutor = new MockCommandExecutor();

    @Test
    public void benchmark_with_no_failures() throws Exception {
        commandExecutor.addCommand("cat " + VALID_DISK_BENCHMARK_PATH);
        commandExecutor.addCommand("cat " + VALID_CPU_BENCHMARK_PATH);
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();
        commandExecutor.addCommand("cat " + VALID_MEMORY_WRITE_BENCHMARK_PATH);
        commandExecutor.addCommand("cat " + VALID_MEMORY_READ_BENCHMARK_PATH);
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();

        String result = Main.execute(new String[] {
                "benchmark",
        }, commandExecutor);

        assertEquals("null", result);
    }

    @Test
    public void disk_benchmark_failure() throws Exception {
        commandExecutor.addCommand("cat " + VALID_SLOW_DISK_BENCHMARK_PATH);
        commandExecutor.addCommand("cat " + VALID_CPU_BENCHMARK_PATH);
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();
        commandExecutor.addCommand("cat " + VALID_MEMORY_WRITE_BENCHMARK_PATH);
        commandExecutor.addCommand("cat " + VALID_MEMORY_READ_BENCHMARK_PATH);
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();

        String result = Main.execute(new String[]{
                "benchmark",
        }, commandExecutor);

        assertEquals("{\"benchmarkReport\":{\"diskSpeedMbs\":49.0}}", result);
    }


    @Test
    public void preserve_previous_spec_verifier_result() throws Exception {
        commandExecutor.addCommand("cat " + VALID_DISK_BENCHMARK_PATH);
        commandExecutor.addCommand("cat " + VALID_CPU_BENCHMARK_PATH);
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();
        commandExecutor.addCommand("cat " + VALID_MEMORY_WRITE_BENCHMARK_PATH);
        commandExecutor.addCommand("cat " + VALID_MEMORY_READ_BENCHMARK_PATH);
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();

        final String previousResult = "{\"specVerificationReport\":{\"actualMemoryAvailable\":4.042128}}";

        String result = Main.execute(new String[] {
                "benchmark",
                "-h", previousResult
        }, commandExecutor);

        assertEquals(previousResult, result);
    }
}
