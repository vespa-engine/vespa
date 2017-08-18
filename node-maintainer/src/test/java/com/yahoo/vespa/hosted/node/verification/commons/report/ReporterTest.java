// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.report;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ReporterTest {

    private final ByteArrayOutputStream println = new ByteArrayOutputStream();
    private static final String ABSOLUTE_PATH = Paths.get(".").toAbsolutePath().normalize().toString();
    private static final String RESOURCE_PATH_TO_VALID_HARDWARE_DIVERGENCE = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json";
    private static final String RESOURCE_PATH_TO_INVALID_HARDWARE_DIVERGENCE = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoNotInterpretableHardwareDivergence.json";
    private static final String RESOURCE_PATH_TO_EMPTY_HARDWARE_DIVERGENCE = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeRepo.json";
    private static final String URL_VALID_RESOURCE_PATH = "file://" + ABSOLUTE_PATH + "/" + RESOURCE_PATH_TO_VALID_HARDWARE_DIVERGENCE;
    private static final String URL_INVALID_RESOURCE_PATH = "file://" + ABSOLUTE_PATH + "/" + RESOURCE_PATH_TO_INVALID_HARDWARE_DIVERGENCE;
    private static final String URL_EMPTY_RESOURCE_PATH = "file://" + ABSOLUTE_PATH + "/" + RESOURCE_PATH_TO_EMPTY_HARDWARE_DIVERGENCE;
    private static List<URL> nodeInfoUrlsToValidHardwareDivergence;
    private static List<URL> nodeInfoUrlsToNOTValidHardwareDivergence;
    private static List<URL> nodeInfoUrlsWithNoHardwareDivergence;

    @Before
    public void setup() throws IOException {
        System.setOut(new PrintStream(println));
        URL nodeInfoUrlWithAlreadyExistingHardwareDivergence = new URL(URL_VALID_RESOURCE_PATH);
        nodeInfoUrlsToValidHardwareDivergence = new ArrayList<>(Arrays.asList(nodeInfoUrlWithAlreadyExistingHardwareDivergence));
        URL nodeInfoUrlWithExistingButWrongHardwareDivergence = new URL(URL_INVALID_RESOURCE_PATH);
        nodeInfoUrlsToNOTValidHardwareDivergence = new ArrayList<>(Arrays.asList(nodeInfoUrlWithExistingButWrongHardwareDivergence));
        URL nodeInfoUrlWithNoHardwareDivergence = new URL(URL_EMPTY_RESOURCE_PATH);
        nodeInfoUrlsWithNoHardwareDivergence = new ArrayList<>(Arrays.asList(nodeInfoUrlWithNoHardwareDivergence));
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
        String expectedReport = "{\"benchmarkReport\":{\"cpuCyclesPerSec\":0.3,\"memoryReadSpeedGBs\":0.1}}";
        Reporter.reportBenchmarkResults(benchmarkReport, nodeInfoUrlsToValidHardwareDivergence);
        assertEquals(expectedReport, println.toString());
    }

    @Test
    public void reportBenchmarkResults_should_should_update_already_existing_hardwareDivergence_prints_null_when_empty_benchmarkReport() throws Exception {
        BenchmarkReport benchmarkReport = new BenchmarkReport();
        String expectedReport = "null";
        Reporter.reportBenchmarkResults(benchmarkReport, nodeInfoUrlsToValidHardwareDivergence);
        assertEquals(expectedReport, println.toString());
    }

    @Test
    public void reportSpecVerificationResults_should_update_already_existing_hardwareDivergence_adding_report_type() throws Exception {
        SpecVerificationReport specVerificationReport = new SpecVerificationReport();
        double actualDiskSpaceAvailable = 150D;
        boolean actualIpv6Connection = false;
        specVerificationReport.setActualDiskSpaceAvailable(actualDiskSpaceAvailable);
        specVerificationReport.setActualIpv6Connection(actualIpv6Connection);
        String expectedReport = "{\"specVerificationReport\":{\"actualDiskSpaceAvailable\":150.0,\"actualIpv6Connection\":false},\"benchmarkReport\":{\"cpuCyclesPerSec\":0.5}}";
        Reporter.reportSpecVerificationResults(specVerificationReport, nodeInfoUrlsToValidHardwareDivergence);
        assertEquals(expectedReport, println.toString());
    }

    @Test
    public void reportSpecVerificationResults_make_new_correct_hardwareDivergence_because_old_is_wrong() throws Exception {
        SpecVerificationReport specVerificationReport = new SpecVerificationReport();
        double actualDiskSpaceAvailable = 150D;
        specVerificationReport.setActualDiskSpaceAvailable(actualDiskSpaceAvailable);
        String expectedReport = "{\"specVerificationReport\":{\"actualDiskSpaceAvailable\":150.0}}";
        Reporter.reportSpecVerificationResults(specVerificationReport, nodeInfoUrlsToNOTValidHardwareDivergence);
        assertEquals(expectedReport, println.toString());
    }

    @Test
    public void reportSpecVerificationResults_make_new_empty_hardwareDivergence_because_there_is_no_old() throws Exception {
        SpecVerificationReport specVerificationReport = new SpecVerificationReport();
        String expectedReport = "null";
        Reporter.reportSpecVerificationResults(specVerificationReport, nodeInfoUrlsWithNoHardwareDivergence);
        assertEquals(expectedReport, println.toString());
    }

}