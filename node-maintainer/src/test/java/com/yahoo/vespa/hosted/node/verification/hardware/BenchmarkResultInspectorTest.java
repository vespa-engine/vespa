// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author sgrostad
 * @author olaaun
 */

public class BenchmarkResultInspectorTest {

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
    public void setup() {
        benchmarkResults = new BenchmarkResults();
        benchmarkResults.setCpuCyclesPerSec(VALID_CPU_FREQUENCY);
        benchmarkResults.setDiskSpeedMbs(VALID_DISK_SPEED);
        benchmarkResults.setMemoryWriteSpeedGBs(VALID_MEMORY_WRITE_SPEED);
        benchmarkResults.setMemoryReadSpeedGBs(VALID_MEMORY_READ_SPEED);
    }

    @Test
    public void isBenchmarkResultsValid_should_return_BenchmarkReport_with_all_values_null() {
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        assertNull(benchmarkReport.getCpuCyclesPerSec());
        assertNull(benchmarkReport.getDiskSpeedMbs());
        assertNull(benchmarkReport.getMemoryReadSpeedGBs());
        assertNull(benchmarkReport.getMemoryWriteSpeedGBs());
    }

    @Test
    public void isBenchmarkResultsValid_should_only_set_cpu_frequency() {
        benchmarkResults.setCpuCyclesPerSec(INVALID_CPU_FREQUENCY);
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        assertNotNull(benchmarkReport.getCpuCyclesPerSec());
        assertNull(benchmarkReport.getDiskSpeedMbs());
        assertNull(benchmarkReport.getMemoryReadSpeedGBs());
        assertNull(benchmarkReport.getMemoryWriteSpeedGBs());
    }

    @Test
    public void isBenchmarkResultsValid_should_only_set_disk_speed() {
        benchmarkResults.setDiskSpeedMbs(INVALID_DISK_SPEED);
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        assertNull(benchmarkReport.getCpuCyclesPerSec());
        assertNotNull(benchmarkReport.getDiskSpeedMbs());
        assertNull(benchmarkReport.getMemoryReadSpeedGBs());
        assertNull(benchmarkReport.getMemoryWriteSpeedGBs());
    }

//    @Test TODO: Temporarily disabled due to Meltdown/Spectre performance impact, see VESPA-11051
    public void isBenchmarkResultsValid_should_only_set_memory_read_speed() {
        benchmarkResults.setMemoryReadSpeedGBs(INVALID_MEMORY_READ_SPEED);
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        assertNull(benchmarkReport.getCpuCyclesPerSec());
        assertNull(benchmarkReport.getDiskSpeedMbs());
        assertNotNull(benchmarkReport.getMemoryReadSpeedGBs());
        assertNull(benchmarkReport.getMemoryWriteSpeedGBs());
    }

//    @Test TODO: Temporarily disabled due to Meltdown/Spectre performance impact, see VESPA-11051
    public void isBenchmarkResultsValid_should_only_set_memory_write_speed() {
        benchmarkResults.setMemoryWriteSpeedGBs(INVALID_MEMORY_WRITE_SPEED);
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        assertNull(benchmarkReport.getCpuCyclesPerSec());
        assertNull(benchmarkReport.getDiskSpeedMbs());
        assertNull(benchmarkReport.getMemoryReadSpeedGBs());
        assertNotNull(benchmarkReport.getMemoryWriteSpeedGBs());
    }

}
