package com.yahoo.vespa.hosted.node.verification.commons;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoJsonModel;
import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;
import com.yahoo.vespa.hosted.node.verification.commons.report.HardwareDivergenceReport;
import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReportSender {

    private static final Logger logger = Logger.getLogger(ReportSender.class.getName());

    private static void updateNodeRepository(ArrayList<URL> nodeInfoUrls, HardwareDivergenceReport hardwareDivergenceReport) throws IOException {
        ObjectMapper om = new ObjectMapper();
        String report;
        if (hardwareDivergenceReport.isHardwareDivergenceReportEmpty()){
            report = "{\"hardwareDivergence\": null}";
        }
        else {
            report = "{\"hardwareDivergence\": " + om.writeValueAsString(hardwareDivergenceReport) + "}";
        }
        HttpPatch httpPatch = new HttpPatch(nodeInfoUrls.get(0).toString());
        httpPatch.setEntity(new StringEntity(report));
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpPatch);
            logger.log(Level.INFO, "Response code: " + httpResponse.getStatusLine().getStatusCode());

        } catch (ClientProtocolException e) {
          System.out.println("Failed to patch node repo - Invalid URL");
        }
        httpClient.close();
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
