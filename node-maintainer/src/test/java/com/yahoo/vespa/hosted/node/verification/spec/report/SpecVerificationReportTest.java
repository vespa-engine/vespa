package com.yahoo.vespa.hosted.node.verification.spec.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpecVerificationReportTest {

    private SpecVerificationReport specVerificationReport;
    private static final String REPORT_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/reportJSON";

    @Before
    public void setup() {
        specVerificationReport = new SpecVerificationReport();
    }

    @Test
    public void VerificationReport_returns_empty_string_when_all_specs_are_correct() throws Exception {
        String expectedJson = "{}";
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(specVerificationReport);
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void Json_is_in_wanted_format_when_all_specs_are_wrong() throws Exception {
        specVerificationReport.setActualInterfaceSpeed(100D);
        specVerificationReport.setActualDiskSpaceAvailable(500D);
        specVerificationReport.setActualDiskType(HardwareInfo.DiskType.FAST);
        specVerificationReport.setActualMemoryAvailable(123D);
        specVerificationReport.setActualcpuCores(4);
        specVerificationReport.setFaultyIpAddresses(new String[]{"2001:4998:44:505d:0:0:0:2618"});
        String expectedJson = MockCommandExecutor.readFromFile(REPORT_PATH).get(0);
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(specVerificationReport);
        assertEquals(expectedJson, actualJson);
    }

}