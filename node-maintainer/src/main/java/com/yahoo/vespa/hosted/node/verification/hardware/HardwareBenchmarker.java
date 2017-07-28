package com.yahoo.vespa.hosted.node.verification.hardware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.Benchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.CPUBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.DiskBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.MemoryBenchmark;
import com.yahoo.vespa.hosted.node.verification.hardware.yamasreport.YamasHardwareReport;

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

        YamasHardwareReport yamasHardwareReport = makeYamasHardwareReport(benchmarkResults);
        printBenchmarkResults(yamasHardwareReport);
        TerminationController.terminateIfInvalidBenchmarkResults(benchmarkResults);
        return true;
    }

    protected static YamasHardwareReport makeYamasHardwareReport(BenchmarkResults benchmarkResults){
        YamasHardwareReport yamasHardwareReport = new YamasHardwareReport();
        yamasHardwareReport.createReportFromBenchmarkResults(benchmarkResults);
        return yamasHardwareReport;
    }

    private static void printBenchmarkResults(YamasHardwareReport yamasHardwareReport){
        ObjectMapper om = new ObjectMapper();
        try {
            System.out.println(om.writeValueAsString(yamasHardwareReport));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        CommandExecutor commandExecutor = new CommandExecutor();
        HardwareBenchmarker.hardwareBenchmarks(commandExecutor);
    }

}
