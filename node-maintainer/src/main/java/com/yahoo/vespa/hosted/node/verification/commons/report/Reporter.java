// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeSpec;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsible for printing hardware divergence report to standard out
 *
 * @author sgrostad
 * @author olaaun
 */
public class Reporter {

    private static final Logger logger = Logger.getLogger(Reporter.class.getName());

    private static void printHardwareDivergenceReport(HardwareDivergenceReport hardwareDivergenceReport) throws IOException {
        ObjectMapper om = new ObjectMapper();
        String report;
        if (hardwareDivergenceReport.isHardwareDivergenceReportEmpty()) {
            report = "null";
        } else {
            report = om.writeValueAsString(hardwareDivergenceReport);
        }
        System.out.print(report);
    }

    public static void reportBenchmarkResults(BenchmarkReport benchmarkReport, List<URL> nodeInfoUrls) throws IOException {
        HardwareDivergenceReport hardwareDivergenceReport = generateHardwareDivergenceReport(nodeInfoUrls);
        hardwareDivergenceReport.setBenchmarkReport(benchmarkReport);
        printHardwareDivergenceReport(hardwareDivergenceReport);
    }

    public static void reportSpecVerificationResults(SpecVerificationReport specVerificationReport, List<URL> nodeInfoUrls) throws IOException {
        HardwareDivergenceReport hardwareDivergenceReport = generateHardwareDivergenceReport(nodeInfoUrls);
        hardwareDivergenceReport.setSpecVerificationReport(specVerificationReport);
        printHardwareDivergenceReport(hardwareDivergenceReport);
    }

    private static HardwareDivergenceReport generateHardwareDivergenceReport(List<URL> nodeInfoUrls) throws IOException {
        NodeSpec nodeSpec = NodeRepoInfoRetriever.retrieve(nodeInfoUrls);
        ObjectMapper om = new ObjectMapper();
        if (nodeSpec.getHardwareDivergence() == null || nodeSpec.getHardwareDivergence().equals("null")) {
            return new HardwareDivergenceReport();
        }
        try {
            HardwareDivergenceReport hardwareDivergenceReport = om.readValue(nodeSpec.getHardwareDivergence(), HardwareDivergenceReport.class);
            return hardwareDivergenceReport;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to parse hardware divergence report from node repo. Report:\n" + nodeSpec.getHardwareDivergence(), e.getMessage());
            return new HardwareDivergenceReport();
        }
    }

}
