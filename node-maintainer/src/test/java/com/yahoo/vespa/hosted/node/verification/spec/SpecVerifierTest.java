package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoJsonModel;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SpecVerifierTest {

    private SpecVerifier specVerifier;
    private MockCommandExecutor mockCommandExecutor;
    private static final String PATH = Paths.get(".").toAbsolutePath().normalize().toString();
    private static final String RESOURCE_PATH = "file://" + PATH + "/src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/";

    @Before
    public void setup() {
        mockCommandExecutor = new MockCommandExecutor();
        specVerifier = new SpecVerifier();
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