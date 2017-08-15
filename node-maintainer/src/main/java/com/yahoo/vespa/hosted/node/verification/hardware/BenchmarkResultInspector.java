package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;

public class BenchmarkResultInspector {

    private static final double CPU_FREQUENCY_LOWER_LIMIT = 0.5;
    private static final double MEMORY_WRITE_SPEED_LOWER_LIMIT = 1D;
    private static final double MEMORY_READ_SPEED_LOWER_LIMIT = 1D;
    private static final double DISK_SPEED_LOWER_LIMIT = 50D;

    public static BenchmarkReport makeBenchmarkReport(BenchmarkResults benchmarkResults) {
        BenchmarkReport benchmarkReport = new BenchmarkReport();
        double cpuCyclesPerSec = benchmarkResults.getCpuCyclesPerSec();
        if (cpuCyclesPerSec < CPU_FREQUENCY_LOWER_LIMIT) {
            benchmarkReport.setCpuCyclesPerSec(cpuCyclesPerSec);
        }
        double memoryWriteSpeed = benchmarkResults.getMemoryWriteSpeedGBs();
        if (memoryWriteSpeed < MEMORY_WRITE_SPEED_LOWER_LIMIT) {
            benchmarkReport.setMemoryWriteSpeedGBs(memoryWriteSpeed);
        }
        double memoryReadSpeed = benchmarkResults.getMemoryReadSpeedGBs();
        if (memoryReadSpeed < MEMORY_READ_SPEED_LOWER_LIMIT) {
            benchmarkReport.setMemoryReadSpeedGBs(memoryReadSpeed);
        }
        double diskSpeed = benchmarkResults.getDiskSpeedMbs();
        if (diskSpeed < DISK_SPEED_LOWER_LIMIT) {
            benchmarkReport.setDiskSpeedMbs(diskSpeed);
        }
        return benchmarkReport;
    }

}
