package com.yahoo.vespa.hosted.node.verification.spec.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class VerificationReportTest {

    private VerificationReport verificationReport;
    private static final String REPORT_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/reportJSON";

    @Before
    public void setup() {
        verificationReport = new VerificationReport();
    }

    @Test
    public void VerificationReport_returns_empty_string_when_all_specs_are_correct() throws Exception {
        String expectedJson = "{}";
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(verificationReport);
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void Json_is_in_wanted_format_when_all_specs_are_wrong() throws Exception {
        verificationReport.setActualInterfaceSpeed(100D);
        verificationReport.setActualDiskSpaceAvailable(500D);
        verificationReport.setActualDiskType(HardwareInfo.DiskType.FAST);
        verificationReport.setActualMemoryAvailable(123D);
        verificationReport.setActualcpuCores(4);
        verificationReport.setFaultyIpAddresses(new String[]{"2001:4998:44:505d:0:0:0:2618"});
        String expectedJson = MockCommandExecutor.readFromFile(REPORT_PATH).get(0);
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(verificationReport);
        assertEquals(expectedJson, actualJson);
    }

}