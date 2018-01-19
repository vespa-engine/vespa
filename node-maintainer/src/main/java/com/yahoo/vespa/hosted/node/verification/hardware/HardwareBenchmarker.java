// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.vespa.hosted.node.verification.Main;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;
import com.yahoo.vespa.hosted.node.verification.commons.report.HardwareDivergenceReport;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.Benchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.CPUBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.DiskBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.MemoryBenchmark;
import io.airlift.command.Command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Benchmarks different hardware components and creates report
 */
@Command(name = "benchmark", description = "Run node benchmarks")
public class HardwareBenchmarker extends Main.VerifierCommand {

    @Override
    public void run(HardwareDivergenceReport hardwareDivergenceReport, CommandExecutor commandExecutor) {
        BenchmarkReport benchmarkReport = hardwareBenchmarks(commandExecutor);

        hardwareDivergenceReport.setBenchmarkReport(benchmarkReport);
    }

    private BenchmarkReport hardwareBenchmarks(CommandExecutor commandExecutor) {
        BenchmarkResults benchmarkResults = new BenchmarkResults();
        List<Benchmark> benchmarks = new ArrayList<>(Arrays.asList(
                new DiskBenchmark(benchmarkResults, commandExecutor),
                new CPUBenchmark(benchmarkResults, commandExecutor),
                new MemoryBenchmark(benchmarkResults, commandExecutor)));
        for (Benchmark benchmark : benchmarks) {
            benchmark.doBenchmark();
        }
        return BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
    }
}
