package com.yahoo.vespa.hosted.node.verification.hardware.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.report.BenchmarkReport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BenchmarkReportTest {

    private BenchmarkReport benchmarkReport = new BenchmarkReport();

    @Test
    public void createFromHardwareResults_should_create_correct_report() throws Exception {
        double expectedCpuCyclesPerSec = 4;
        double expectedDiskSpeedMbps = 120;
        double expectedMemoryReadSpeedGBs = 7.1;
        double expectedMemoryWriteSpeedGBs = 5.9;
        benchmarkReport.setCpuCyclesPerSec(expectedCpuCyclesPerSec);
        benchmarkReport.setDiskSpeedMbs(expectedDiskSpeedMbps);
        benchmarkReport.setMemoryReadSpeedGBs(expectedMemoryReadSpeedGBs);
        benchmarkReport.setMemoryWriteSpeedGBs(expectedMemoryWriteSpeedGBs);
        ObjectMapper om = new ObjectMapper();
        String expectedResultJson = "{\"cpuCyclesPerSec\":4.0,\"diskSpeedMbs\":120.0,\"memoryWriteSpeedGBs\":5.9,\"memoryReadSpeedGBs\":7.1}";
        assertEquals(expectedResultJson, om.writeValueAsString(benchmarkReport));
    }

}