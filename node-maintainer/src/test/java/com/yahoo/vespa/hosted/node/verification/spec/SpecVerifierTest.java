package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeJsonConverter;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoJsonModel;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.yamasreport.YamasSpecReport;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SpecVerifierTest {

    private SpecVerifier specVerifier;
    private MockCommandExecutor mockCommandExecutor;
    private static final String PATH = Paths.get(".").toAbsolutePath().normalize().toString();
    private static final String RESOURCE_PATH = "file://" + PATH + "/src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/";
    private static final String NODE_REPO_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json";

    @Before
    public void setup() {
        mockCommandExecutor = new MockCommandExecutor();
        specVerifier = new SpecVerifier();
    }

    @Test
    public void makeYamasSpecReport_should_return_false_interface_speed() throws Exception {
        HardwareInfo actualHardware = new HardwareInfo();
        actualHardware.setMinCpuCores(24);
        actualHardware.setMinMainMemoryAvailableGb(24);
        actualHardware.setInterfaceSpeedMbs(10009); //this is wrong
        actualHardware.setMinDiskAvailableGb(500);
        actualHardware.setIpv4Connectivity(true);
        actualHardware.setIpv6Connectivity(false);
        actualHardware.setDiskType(HardwareInfo.DiskType.SLOW);
        URL url = new File(NODE_REPO_PATH).toURI().toURL();
        NodeRepoJsonModel nodeRepoJsonModel = NodeRepoInfoRetriever.retrieve(url);
        YamasSpecReport yamasSpecReport = specVerifier.makeYamasSpecReport(actualHardware, nodeRepoJsonModel);
        long timeStamp = yamasSpecReport.getTimeStamp();
        String expectedJson = "{\"timeStamp\":" + timeStamp + ",\"dimensions\":{\"memoryMatch\":true,\"cpuCoresMatch\":true,\"diskTypeMatch\":true,\"netInterfaceSpeedMatch\":false,\"diskAvailableMatch\":true,\"ipv4Match\":true,\"ipv6Match\":true},\"metrics\":{\"match\":false,\"expectedInterfaceSpeed\":1000.0,\"actualInterfaceSpeed\":10009.0},\"routing\":{\"yamas\":{\"namespace\":[\"Vespa\"]}}}";
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(yamasSpecReport);
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void getNodeRepositoryJSON_should_return_valid_nodeRepoJSONModel() throws Exception {
        mockCommandExecutor.addCommand("echo nodeRepo.json");
        NodeRepoJsonModel actualNodeRepoJsonModel = specVerifier.getNodeRepositoryJSON(RESOURCE_PATH, mockCommandExecutor);
        NodeRepoJsonModel expectedNodeRepoJsonModel = new NodeRepoJsonModel();
        String expectedIpv6Address = "2001:4998:c:2940::111c";
        assertEquals(expectedIpv6Address, actualNodeRepoJsonModel.getIpv6Address());

    }

}