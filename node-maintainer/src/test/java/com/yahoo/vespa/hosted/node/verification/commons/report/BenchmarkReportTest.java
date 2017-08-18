// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author sgrostad
 * @author olaaun
 */

public class BenchmarkReportTest {

    private BenchmarkReport benchmarkReport = new BenchmarkReport();

    @Test
    public void create_report_from_BenchmarkResults_should_create_correct_report() throws Exception {
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
