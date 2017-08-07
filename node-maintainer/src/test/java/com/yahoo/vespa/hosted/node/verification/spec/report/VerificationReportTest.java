package com.yahoo.vespa.hosted.node.verification.spec.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class VerificationReportTest {

    private VerificationReport verificationReport;
    private static final String YAMAS_REPORT_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/reportJSON";


    @Before
    public void setup() {
        verificationReport = new VerificationReport();
        verificationReport.setActualInterfaceSpeed(100D);
        verificationReport.setActualDiskSpaceAvailable(500D);
        verificationReport.setActualDiskType(HardwareInfo.DiskType.FAST);
        verificationReport.setActualMemoryAvailable(123D);
        verificationReport.setActualcpuCores(4);
    }

    @Test
    public void Json_is_in_wanted_format() throws Exception {

        String expectedJson = MockCommandExecutor.readFromFile(YAMAS_REPORT_PATH).get(0);
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(verificationReport);
        assertEquals(expectedJson, actualJson);
    }

}