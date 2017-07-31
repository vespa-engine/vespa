package com.yahoo.vespa.hosted.node.verification.spec.yamasreport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by olaa on 12/07/2017.
 */
public class YamasSpecReportTest {

    private SpecReportDimensions specReportDimensions;
    private SpecReportMetrics specReportMetrics;
    private static final String YAMAS_REPORT_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/yamasJSON";


    @Before
    public void setup() {
        specReportDimensions = new SpecReportDimensions();
        specReportMetrics = new SpecReportMetrics();
        specReportDimensions.setCpuCoresMatch(true);
        specReportDimensions.setDiskAvailableMatch(true);
        specReportDimensions.setIpv4Match(true);
        specReportDimensions.setIpv6Match(true);
        specReportDimensions.setMemoryMatch(true);
        specReportDimensions.setNetInterfaceSpeedMatch(true);
        specReportDimensions.setDiskTypeMatch(true);
        specReportMetrics.setActualInterfaceSpeed(100D);
        specReportMetrics.setExpectedInterfaceSpeed(100D);
        specReportMetrics.setActualDiskSpaceAvailable(500D);
        specReportMetrics.setExpectedDiskSpaceAvailable(500D);
        specReportMetrics.setActualDiskType(HardwareInfo.DiskType.FAST);
        specReportMetrics.setExpectedDiskType(HardwareInfo.DiskType.FAST);
        specReportMetrics.setActualMemoryAvailable(123D);
        specReportMetrics.setExpectedMemoryAvailable(123D);
        specReportMetrics.setActualcpuCores(4);
        specReportMetrics.setExpectedcpuCores(4);
        specReportMetrics.setMatch(true);
    }

    @Test
    public void Json_is_in_wanted_format() throws Exception {
        YamasSpecReport yamasSpecReport = new YamasSpecReport();
        yamasSpecReport.setMetrics(specReportMetrics);
        yamasSpecReport.setDimensions(specReportDimensions);
        yamasSpecReport.setMetrics(specReportMetrics);
        long timeStamp = 1501504035;
        yamasSpecReport.setTimeStamp(timeStamp);
        String expectedJson = MockCommandExecutor.readFromFile(YAMAS_REPORT_PATH).get(0);
        ObjectMapper om = new ObjectMapper();
        String json = om.writeValueAsString(yamasSpecReport);
        assertEquals(expectedJson, json);
    }

}