// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.Main;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class SpecVerifierTest {

    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources";
    private static final String CPU_INFO_PATH = RESOURCE_PATH + "/cpuinfoTest";
    private static final String MEMORY_INFO_PATH = RESOURCE_PATH + "/meminfoTest";
    private static final String FAST_DISK_TYPE_INFO_PATH = RESOURCE_PATH + "/DiskTypeFastDisk";
    private static final String DISK_SIZE_INFO_PATH = RESOURCE_PATH + "/filesize";
    private static final String NET_INTERFACE_INFO_PATH = RESOURCE_PATH + "/ifconfig";
    private static final String NET_INTERFACE_SPEED_INFO_PATH = RESOURCE_PATH + "/eth0";
    private static final String PING_RESPONSE = RESOURCE_PATH + "/validpingresponse";
    private final MockCommandExecutor commandExecutor = new MockCommandExecutor();


    @Test
    public void spec_verification_with_failures() throws Exception {
        commandExecutor.addCommand("cat " + CPU_INFO_PATH);
        commandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        commandExecutor.addCommand("cat " + FAST_DISK_TYPE_INFO_PATH);
        commandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        commandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH);
        commandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        commandExecutor.addCommand("cat " + PING_RESPONSE);

        String result = Main.execute(new String[] {
                "specification",
                "-d", "250",
                "-m", "64",
                "-c", "1.5",
                "-s", "true",
                "-i", "10.11.12.13,::1234"
        }, commandExecutor);

        assertEquals(
                "{\"specVerificationReport\":{\"" +
                        "actualMemoryAvailable\":4.042128,\"" +
                        "actualDiskSpaceAvailable\":1760.0,\"" +
                        "actualcpuCores\":4,\"" +
                        "faultyIpAddresses\":[\"10.11.12.13\",\"0:0:0:0:0:0:0:1234\"]}}", result);
    }

    @Test
    public void benchmark_with_no_failures() throws Exception {
        commandExecutor.addCommand("cat " + CPU_INFO_PATH);
        commandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        commandExecutor.addCommand("cat " + FAST_DISK_TYPE_INFO_PATH);
        commandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        commandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH);
        commandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        commandExecutor.addCommand("cat " + PING_RESPONSE);

        String result = Main.execute(new String[] {
                "specification",
                "-d", "1760",
                "-m", "4",
                "-c", "4",
                "-s", "true",
        }, commandExecutor);

        assertEquals("null", result);
    }

    @Test
    public void preserve_previous_benchmark_result() throws Exception {
        commandExecutor.addCommand("cat " + CPU_INFO_PATH);
        commandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        commandExecutor.addCommand("cat " + FAST_DISK_TYPE_INFO_PATH);
        commandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        commandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH);
        commandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        commandExecutor.addCommand("cat " + PING_RESPONSE);

        final String previousResult = "{\"specVerificationReport\":{\"actualMemoryAvailable\":4.042128}," +
                "\"benchmarkReport\":{\"diskSpeedMbs\":49.0}}";

        String result = Main.execute(new String[] {
                "specification",
                "-d", "1760",
                "-m", "4",
                "-c", "4",
                "-s", "true",
                "-h", previousResult
        }, commandExecutor);

        assertEquals("{\"benchmarkReport\":{\"diskSpeedMbs\":49.0}}", result);
    }
}
