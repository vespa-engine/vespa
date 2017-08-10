package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoJsonModel;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

public class ReportSender {

    private static void printHardwareDivergenceReport(HardwareDivergenceReport hardwareDivergenceReport) throws IOException {
        ObjectMapper om = new ObjectMapper();
        String report;
        if (hardwareDivergenceReport.isHardwareDivergenceReportEmpty()){
            report = "{\"hardwareDivergence\": null}";
        }
        else {
            report = "{\"hardwareDivergence\": \"" + om.writeValueAsString(hardwareDivergenceReport) + "\"}";
        }
        System.out.println(om.writeValueAsString(report));
    }

    public static void reportBenchmarkResults(BenchmarkReport benchmarkReport, ArrayList<URL> nodeInfoUrls) throws IOException {
        HardwareDivergenceReport hardwareDivergenceReport = generateHardwareDivergenceReport(nodeInfoUrls);
        hardwareDivergenceReport.setBenchmarkReport(benchmarkReport);
        printHardwareDivergenceReport(hardwareDivergenceReport);
    }

    public static void reportSpecVerificationResults(SpecVerificationReport specVerificationReport, ArrayList<URL> nodeInfoUrls) throws IOException {
        HardwareDivergenceReport hardwareDivergenceReport = generateHardwareDivergenceReport(nodeInfoUrls);
        hardwareDivergenceReport.setSpecVerificationReport(specVerificationReport);
        printHardwareDivergenceReport(hardwareDivergenceReport);
    }

    private static HardwareDivergenceReport generateHardwareDivergenceReport(ArrayList<URL> nodeInfoUrls) throws IOException {
        NodeRepoJsonModel nodeRepoJsonModel = NodeRepoInfoRetriever.retrieve(nodeInfoUrls);
        ObjectMapper om = new ObjectMapper();
        if (nodeRepoJsonModel.getHardwareDivergence() == null) {
            return new HardwareDivergenceReport();
        }
        return om.readValue(nodeRepoJsonModel.getHardwareDivergence(), HardwareDivergenceReport.class);
    }
}
