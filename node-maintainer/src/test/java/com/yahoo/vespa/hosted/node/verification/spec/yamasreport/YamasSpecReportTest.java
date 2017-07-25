package com.yahoo.vespa.hosted.node.verification.spec.yamasreport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by olaa on 12/07/2017.
 */
public class YamasSpecReportTest {
    
    SpecReportDimensions specReportDimensions;
    SpecReportMetrics specReportMetrics;
    
    @Before
    public void setup(){
        specReportDimensions = new SpecReportDimensions();
        specReportMetrics = new SpecReportMetrics();
        specReportDimensions.setCpuCoresMatch(true);
        specReportDimensions.setDiskAvailableMatch(true);
        specReportDimensions.setIpv4Match(true);
        specReportDimensions.setIpv6Match(true);
        specReportDimensions.setMemoryMatch(true);
        specReportDimensions.setNetInterfaceSpeedMatch(true);
        specReportDimensions.setFastDiskMatch(true);
        specReportMetrics.setActualInterfaceSpeed(100D);
        specReportMetrics.setExpectedInterfaceSpeed(100D);
        specReportMetrics.setActualDiskSpaceAvailable(500D);
        specReportMetrics.setExpectedDiskSpaceAvailable(500D);
        specReportMetrics.setActualDiskType(true);
        specReportMetrics.setExpectedDiskType(true);
        specReportMetrics.setActualMemoryAvailable(123D);
        specReportMetrics.setExpectedMemoryAvailable(123D);
        specReportMetrics.setActualcpuCores(4);
        specReportMetrics.setExpectedcpuCores(4);
        specReportMetrics.setMatch(true);
    }

    @Test
    public void Json_is_in_wanted_format() throws Exception{
        YamasSpecReport yamasSpecReport = new YamasSpecReport();
        yamasSpecReport.setMetrics(specReportMetrics);
        yamasSpecReport.setDimensions(specReportDimensions);
        yamasSpecReport.setMetrics(specReportMetrics);
        long time = yamasSpecReport.getTimeStamp();
        String expectedJson = "{\"timeStamp\":" + time + ",\"dimensions\":{\"memoryMatch\":true,\"cpuCoresMatch\":true,\"fastDiskMatch\":true,\"netInterfaceSpeedMatch\":true,\"diskAvailableMatch\":true,\"ipv4Match\":true,\"ipv6Match\":true},\"metrics\":{\"match\":true,\"expectedMemoryAvailable\":123.0,\"actualMemoryAvailable\":123.0,\"expectedFastDisk\":true,\"actualFastDisk\":true,\"expectedDiskSpaceAvailable\":500.0,\"actualDiskSpaceAvailable\":500.0,\"expectedInterfaceSpeed\":100.0,\"actualInterfaceSpeed\":100.0,\"expectedcpuCores\":4,\"actualcpuCores\":4},\"routing\":{\"yamas\":{\"namespace\":[\"Vespa\"]}}}";
        ObjectMapper om = new ObjectMapper();
        String json = om.writeValueAsString(yamasSpecReport);
        assertEquals(expectedJson, json);
    }

}