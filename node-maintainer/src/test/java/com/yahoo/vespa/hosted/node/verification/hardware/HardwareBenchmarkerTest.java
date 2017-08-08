package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

public class HardwareBenchmarkerTest {

    private MockCommandExecutor mockCommandExecutor;
    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/";
    private static final String VALID_DISK_BENCHMARK_PATH = RESOURCE_PATH + "diskBenchmarkValidOutput";
    private static final String VALID_CPU_BENCHMARK_PATH = RESOURCE_PATH + "cpuCyclesWithCommasTimeWithDotTest.txt";
    private static final String VALID_MEMORY_WRITE_BENCHMARK_PATH = RESOURCE_PATH + "validMemoryWriteSpeed";
    private static final String VALID_MEMORY_READ_BENCHMARK_PATH = RESOURCE_PATH + "validMemoryReadSpeed";

    @Before
    public void setup() {
        mockCommandExecutor = new MockCommandExecutor();

    }

    @Test
    public void hardwareBenchmarks_should_return_true() throws Exception {
        mockCommandExecutor.addCommand("cat " + VALID_DISK_BENCHMARK_PATH);
        mockCommandExecutor.addCommand("cat " + VALID_CPU_BENCHMARK_PATH);
        mockCommandExecutor.addDummyCommand();
        mockCommandExecutor.addDummyCommand();
        mockCommandExecutor.addCommand("cat " + VALID_MEMORY_WRITE_BENCHMARK_PATH);
        mockCommandExecutor.addCommand("cat " + VALID_MEMORY_READ_BENCHMARK_PATH);
        mockCommandExecutor.addDummyCommand();
        mockCommandExecutor.addDummyCommand();
        assertTrue(HardwareBenchmarker.hardwareBenchmarks(mockCommandExecutor, new ArrayList<>()));
    }


}