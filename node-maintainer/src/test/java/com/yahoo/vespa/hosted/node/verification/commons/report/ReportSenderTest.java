package com.yahoo.vespa.hosted.node.verification.commons.report;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ReportSenderTest {

    private final ByteArrayOutputStream println = new ByteArrayOutputStream();
    private static final String ABSOLUTE_PATH = Paths.get(".").toAbsolutePath().normalize().toString();
    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json";
    private static final String URL_RESOURCE_PATH = "file://" + ABSOLUTE_PATH + "/" + RESOURCE_PATH;
    private static ArrayList<URL> nodeInfoUrls;

    @Before
    public void setup() throws IOException {
        System.setOut(new PrintStream(println));
        URL nodeInfoUrlWithAlreadyExistingHardwareDivergence = new URL(URL_RESOURCE_PATH);
        nodeInfoUrls = new ArrayList<>(Arrays.asList(nodeInfoUrlWithAlreadyExistingHardwareDivergence));
    }

    @After
    public void cleanUpStream() {
        System.setOut(System.out);
    }

    @Test
    public void reportBenchmarkResults_should_update_already_existing_hardwareDivergence_changing_existing_values() throws Exception {
        BenchmarkReport benchmarkReport = new BenchmarkReport();
        double cpuCyclesPerSec = 0.3;
        double memoryReadSpeedGBs = 0.1;
        benchmarkReport.setCpuCyclesPerSec(cpuCyclesPerSec);
        benchmarkReport.setMemoryReadSpeedGBs(memoryReadSpeedGBs);
        String expectedReport = "{\"hardwareDivergence\": \"{\"benchmarkReport\":{\"cpuCyclesPerSec\":0.3,\"memoryReadSpeedGBs\":0.1}}\"}";
        ReportSender.reportBenchmarkResults(benchmarkReport,nodeInfoUrls);
        assertEquals(expectedReport, println.toString());
    }

    @Test
    public void reportBenchmarkResults_should_should_update_already_existing_hardwareDivergence_prints_null_when_empty_benchmarkReport() throws Exception {
        BenchmarkReport benchmarkReport = new BenchmarkReport();
        String expectedReport = "{\"hardwareDivergence\": null}";
        ReportSender.reportBenchmarkResults(benchmarkReport, nodeInfoUrls);
        assertEquals(expectedReport, println.toString());
    }

    @Test
    public void reportSpecVerificationResults_should_update_already_existing_hardwareDivergence_adding_report_type() throws Exception {
        SpecVerificationReport specVerificationReport = new SpecVerificationReport();
        double actualDiskSpaceAvailable = 150D;
        boolean actualIpv6Connection = false;
        specVerificationReport.setActualDiskSpaceAvailable(actualDiskSpaceAvailable);
        specVerificationReport.setActualIpv6Connection(actualIpv6Connection);
        String expectedReport = "{\"hardwareDivergence\": \"{\"specVerificationReport\":{\"actualDiskSpaceAvailable\":150.0,\"actualIpv6Connection\":false},\"benchmarkReport\":{\"cpuCyclesPerSec\":0.5}}\"}";
        ReportSender.reportSpecVerificationResults(specVerificationReport, nodeInfoUrls);
        assertEquals(expectedReport, println.toString());
    }

}