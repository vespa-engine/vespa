package com.yahoo.vespa.hosted.node.verification.hardware;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.Benchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.CPUBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.DiskBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.MemoryBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.report.BenchmarkReport;
import com.yahoo.vespa.hosted.node.verification.spec.report.VerificationReport;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Benchmarks different hardware components and creates report
 */
public class HardwareBenchmarker {

    public static boolean hardwareBenchmarks(CommandExecutor commandExecutor) {
        BenchmarkResults benchmarkResults = new BenchmarkResults();
        ArrayList<Benchmark> benchmarks = new ArrayList<>(Arrays.asList(
                new DiskBenchmark(benchmarkResults, commandExecutor),
                new CPUBenchmark(benchmarkResults, commandExecutor),
                new MemoryBenchmark(benchmarkResults, commandExecutor)));
        for (Benchmark benchmark : benchmarks) {
            benchmark.doBenchmark();
        }
        BenchmarkReport benchmarkReport = BenchmarkResultInspector.makeBenchmarkReport(benchmarkResults);
        printBenchmarkResults(benchmarkReport);
        return isAllBenchmarksOK(benchmarkReport);
    }

    private static boolean isAllBenchmarksOK(BenchmarkReport benchmarkReport) {
        ObjectMapper om = new ObjectMapper();
        try {
            String jsonReport = om.writeValueAsString(benchmarkReport);
            return jsonReport.length() == 2;
        } catch (JsonProcessingException e){
            e.printStackTrace();
            return false;
        }
    }

    private static void printBenchmarkResults(BenchmarkReport benchmarkReport) {
        ObjectMapper om = new ObjectMapper();
        try {
            System.out.println(om.writeValueAsString(benchmarkReport));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        CommandExecutor commandExecutor = new CommandExecutor();
        if (!HardwareBenchmarker.hardwareBenchmarks(commandExecutor)){
            System.exit(2);
        }
    }

}
