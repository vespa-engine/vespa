package com.yahoo.vespa.hosted.node.verification.hardware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.*;
import com.yahoo.vespa.hosted.node.verification.hardware.yamasreport.YamasHardwareReport;

import java.util.ArrayList;
import java.util.Arrays;

public class HardwareVerifier {

    public static void verifyHardware() {
        HardwareResults hardwareResults = new HardwareResults();
        CommandExecutor commandExecutor = new CommandExecutor();

        ArrayList<Benchmark> benchmarks = new ArrayList<>(Arrays.asList(
                new DiskBenchmark(hardwareResults, commandExecutor),
                new CPUBenchmark(hardwareResults, commandExecutor),
                new MemoryBenchmark(hardwareResults, commandExecutor),
                new NetBenchmark(hardwareResults, commandExecutor)));

        for (Benchmark benchmark : benchmarks) {
            benchmark.doBenchmark();
        }

        YamasHardwareReport yamasHardwareReport = new YamasHardwareReport();
        yamasHardwareReport.createFromHardwareResults(hardwareResults);
        ObjectMapper om = new ObjectMapper();
        try {
            System.out.println(om.writeValueAsString(yamasHardwareReport));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        HardwareVerifier.verifyHardware();
    }

}
