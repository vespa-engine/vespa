package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TerminationController {

    private static final Logger logger = Logger.getLogger(TerminationController.class.getName());

    private static final double CPU_FREQUENCY_LOWER_LIMIT = 0.5;
    private static final double MEMORY_WRITE_SPEED_LOWER_LIMIT = 1D;
    private static final double MEMORY_READ_SPEED_LOWER_LIMIT = 1D;
    private static final double DISK_SPEED_LOWER_LIMIT = 50D;

    public static void terminateIfInvalidBenchmarkResults(BenchmarkResults benchmarkResults) {
        if (!isBenchmarkResultsValid(benchmarkResults)) {
            System.exit(1);
        }
    }

    public static boolean isBenchmarkResultsValid(BenchmarkResults benchmarkResults) {
        boolean validResults = true;

        if (benchmarkResults.getCpuCyclesPerSec() < CPU_FREQUENCY_LOWER_LIMIT) {
            logger.log(Level.WARNING, "CPU frequency below accepted value. Value: " + benchmarkResults.getCpuCyclesPerSec() + " GHz");
            validResults = false;
        }

        if (benchmarkResults.getMemoryWriteSpeedGBs() < MEMORY_WRITE_SPEED_LOWER_LIMIT) {
            logger.log(Level.WARNING, "Memory write speed below accepted value. Value: " + benchmarkResults.getMemoryWriteSpeedGBs() + " GB/s");
            validResults = false;
        }

        if (benchmarkResults.getMemoryReadSpeedGBs() < MEMORY_READ_SPEED_LOWER_LIMIT) {
            logger.log(Level.WARNING, "Memory read speed below accepted value. Value: " + benchmarkResults.getMemoryReadSpeedGBs() + " GB/s");
            validResults = false;
        }

        if (benchmarkResults.getDiskSpeedMbs() < DISK_SPEED_LOWER_LIMIT) {
            logger.log(Level.WARNING, "Disk speed below accepted value. Value: " + benchmarkResults.getDiskSpeedMbs() + " MB/s");
            validResults = false;
        }
        return validResults;
    }

}
