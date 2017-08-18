// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware;

import com.yahoo.log.LogSetup;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.HostURLGenerator;
import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;
import com.yahoo.vespa.hosted.node.verification.commons.report.Reporter;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.Benchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.CPUBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.DiskBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.MemoryBenchmark;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Benchmarks different hardware components and creates report
 */
public class HardwareBenchmarker {

    private static final Logger logger = Logger.getLogger(HardwareBenchmarker.class.getName());

    public static boolean hardwareBenchmarks(CommandExecutor commandExecutor, List<URL> nodeInfoUrls) throws IOException {
        BenchmarkResults benchmarkResults = new BenchmarkResults();
        List<Benchmark> benchmarks = new ArrayList<>(Arrays.asList(
                new DiskBenchmark(benchmarkResults, commandExecutor),
                new CPUBenchmark(benchmarkResults, commandExecutor),
                new MemoryBenchmark(benchmarkResults, commandExecutor)));
        for (Benchmark benchmark : benchmarks) {
            benchmark.doBenchmark();
        }
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        Reporter.reportBenchmarkResults(benchmarkReport, nodeInfoUrls);
        return benchmarkReport.isAllBenchmarksOK();
    }

    public static void main(String[] args) throws IOException {
        LogSetup.initVespaLogging("hardware-benchmarker");
        CommandExecutor commandExecutor = new CommandExecutor();
        List<URL> nodeInfoUrls;
        if (args.length == 0) {
            throw new IllegalStateException("Expected config server URL as parameter");
        }
        try {
            nodeInfoUrls = HostURLGenerator.generateNodeInfoUrl(commandExecutor, args[0]);
            HardwareBenchmarker.hardwareBenchmarks(commandExecutor, nodeInfoUrls);
        } catch (IOException e) {
            logger.log(Level.WARNING, e.getMessage());
        }

    }

}
