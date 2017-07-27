package com.yahoo.vespa.hosted.node.verification.hardware.yamasreport;

import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import org.junit.Test;

import static org.junit.Assert.*;

public class YamasHardwareReportTest {

    private YamasHardwareReport yamasHardwareReport = new YamasHardwareReport();
    private static final double DELTA = 0.1;

    @Test
    public void createFromHardwareResults_should_create_correct_report () {
        double expectedCpuCyclesPerSec = 4;
        double expectedDiskSpeedMbps = 120;
        boolean expectedIpv6Connectivity = true;
        double expectedMemoryReadSpeedGBs = 7.1;
        double expectedMemoryWriteSpeedGBs = 5.9;
        BenchmarkResults benchmarkResults = new BenchmarkResults();
        benchmarkResults.setCpuCyclesPerSec(expectedCpuCyclesPerSec);
        benchmarkResults.setDiskSpeedMbs(expectedDiskSpeedMbps);
        benchmarkResults.setMemoryReadSpeedGBs(expectedMemoryReadSpeedGBs);
        benchmarkResults.setMemoryWriteSpeedGBs(expectedMemoryWriteSpeedGBs);
        yamasHardwareReport.createReportFromBenchmarkResults(benchmarkResults);
        assertEquals(expectedCpuCyclesPerSec, yamasHardwareReport.getMetrics().getCpuCyclesPerSec(), DELTA);
        assertEquals(expectedDiskSpeedMbps, yamasHardwareReport.getMetrics().getDiskSpeedMbs(), DELTA);
        assertEquals(expectedMemoryReadSpeedGBs, yamasHardwareReport.getMetrics().getMemoryReadSpeedGBs(), DELTA);
        assertEquals(expectedMemoryWriteSpeedGBs, yamasHardwareReport.getMetrics().getMemoryWriteSpeedGBs(), DELTA);
    }

}