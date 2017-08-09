package com.yahoo.vespa.hosted.node.verification.hardware;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.HostURLGenerator;
import com.yahoo.vespa.hosted.node.verification.commons.ReportSender;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.Benchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.CPUBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.DiskBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.MemoryBenchmark;
import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Benchmarks different hardware components and creates report
 */
public class HardwareBenchmarker {

    public static boolean hardwareBenchmarks(CommandExecutor commandExecutor, ArrayList<URL> nodeInfoUrls) throws IOException {
        BenchmarkResults benchmarkResults = new BenchmarkResults();
        ArrayList<Benchmark> benchmarks = new ArrayList<>(Arrays.asList(
                new DiskBenchmark(benchmarkResults, commandExecutor),
                new CPUBenchmark(benchmarkResults, commandExecutor),
                new MemoryBenchmark(benchmarkResults, commandExecutor)));
        for (Benchmark benchmark : benchmarks) {
            benchmark.doBenchmark();
        }
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        ReportSender.reportBenchmarkResults(benchmarkReport, nodeInfoUrls);
        return benchmarkReport.isAllBenchmarksOK();
    }

    public static void main(String[] args) throws IOException {
        CommandExecutor commandExecutor = new CommandExecutor();
        ArrayList<URL> nodeInfoUrls;
        if (args.length == 0) {
            nodeInfoUrls = HostURLGenerator.generateNodeInfoUrl(commandExecutor);
        } else {
            nodeInfoUrls = HostURLGenerator.generateNodeInfoUrl(commandExecutor, args[0]);
        }
        if (!HardwareBenchmarker.hardwareBenchmarks(commandExecutor, nodeInfoUrls)){
            System.exit(2);
        }
    }

}
