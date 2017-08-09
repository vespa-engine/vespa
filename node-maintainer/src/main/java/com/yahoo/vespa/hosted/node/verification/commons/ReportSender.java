package com.yahoo.vespa.hosted.node.verification.commons;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Response;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoJsonModel;
import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;
import com.yahoo.vespa.hosted.node.verification.commons.report.HardwareDivergenceReport;
import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

public class ReportSender {

    private static void updateNodeRepository(ArrayList<URL> nodeInfoUrls, HardwareDivergenceReport hardwareDivergenceReport) throws IOException {
        ObjectMapper om = new ObjectMapper();
        String report;
        if (hardwareDivergenceReport.isHardwareDivergenceReportEmpty()){
            report = "{\"hardwareDivergence\": null}";
        }
        else {
            report = "{\"hardwareDivergence\": " + om.writeValueAsString(hardwareDivergenceReport) + "}";
        }
        System.out.println(report);
        /*
        //TODO: Update node repo
        String url = nodeInfoUrls.get(0).toString();
        Request request = new Request(url, Utf8.toBytes(report), Request.Method.PATCH);
        JDisc container = JDisc.fromServicesXml("<jdisc version=\"1.0\"/>", Networking.disable);
        Response response = container.handleRequest(request);
        container.close();

        for (URL nodeInfoUrl : nodeInfoUrls) {
            new Request(nodeInfoUrl.toString(),
                    Utf8.toBytes(report),
                    Request.Method.PATCH);

        }*/
    }

    public static void reportBenchmarkResults(BenchmarkReport benchmarkReport, ArrayList<URL> nodeInfoUrls) throws IOException {
        HardwareDivergenceReport hardwareDivergenceReport = generateHardwareDivergenceReport(nodeInfoUrls);
        hardwareDivergenceReport.setBenchmarkReport(benchmarkReport);
        updateNodeRepository(nodeInfoUrls, hardwareDivergenceReport);
    }

    public static void reportSpecVerificationResults(SpecVerificationReport specVerificationReport, ArrayList<URL> nodeInfoUrls) throws IOException {
        HardwareDivergenceReport hardwareDivergenceReport = generateHardwareDivergenceReport(nodeInfoUrls);
        hardwareDivergenceReport.setSpecVerificationReport(specVerificationReport);
        updateNodeRepository(nodeInfoUrls, hardwareDivergenceReport);
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
