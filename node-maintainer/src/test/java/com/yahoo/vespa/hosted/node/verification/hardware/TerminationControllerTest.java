package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class TerminationControllerTest {

    private BenchmarkResults benchmarkResults;
    private static final double VALID_CPU_FREQUENCY = 2.031;
    private static final double INVALID_CPU_FREQUENCY = 0.1;
    private static final double VALID_DISK_SPEED = 1100.0;
    private static final double INVALID_DISK_SPEED = 0.1;
    private static final double VALID_MEMORY_WRITE_SPEED = 1.7;
    private static final double INVALID_MEMORY_WRITE_SPEED = 0.1;
    private static final double VALID_MEMORY_READ_SPEED = 4.3;
    private static final double INVALID_MEMORY_READ_SPEED = 0.1;

    @Before
    public void setup(){
        benchmarkResults = new BenchmarkResults();
        benchmarkResults.setCpuCyclesPerSec(VALID_CPU_FREQUENCY);
        benchmarkResults.setDiskSpeedMbs(VALID_DISK_SPEED);
        benchmarkResults.setMemoryWriteSpeedGBs(VALID_MEMORY_WRITE_SPEED);
        benchmarkResults.setMemoryReadSpeedGBs(VALID_MEMORY_READ_SPEED);
    }

    @Test
    public void isBenchmarkResultsValid_should_return_true(){
        assertTrue(TerminationController.isBenchmarkResultsValid(benchmarkResults));
    }

    @Test
    public void isBenchmarkResultsValid_should_be_false_because_of_cpu_frequency(){
        benchmarkResults.setCpuCyclesPerSec(INVALID_CPU_FREQUENCY);
        assertFalse(TerminationController.isBenchmarkResultsValid(benchmarkResults));
    }

    @Test
    public void isBenchmarkResultsValid_should_be_false_because_of_disk_speed(){
        benchmarkResults.setDiskSpeedMbs(INVALID_DISK_SPEED);
        assertFalse(TerminationController.isBenchmarkResultsValid(benchmarkResults));
    }

    @Test
    public void isBenchmarkResultsValid_should_be_false_because_of_memory_write_speed(){
        benchmarkResults.setMemoryWriteSpeedGBs(INVALID_MEMORY_WRITE_SPEED);
        assertFalse(TerminationController.isBenchmarkResultsValid(benchmarkResults));
    }

    @Test
    public void isBenchmarkResultsValid_should_be_false_because_of_memory_read_speed(){
        benchmarkResults.setMemoryReadSpeedGBs(INVALID_MEMORY_READ_SPEED);
        assertFalse(TerminationController.isBenchmarkResultsValid(benchmarkResults));
    }

}