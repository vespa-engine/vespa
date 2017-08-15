package com.yahoo.vespa.hosted.node.verification.commons;

import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by olaa on 14/07/2017.
 */
public class HostURLGeneratorTest {

    private MockCommandExecutor mockCommandExecutor;
    private static final String CAT_NODE_HOST_NAME_PATH = "cat src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/hostURLGeneratorTest";
    private static final String CAT_WRONG_HOSTNAME_PATH = "cat src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/hostURLGeneratorExceptionTest";
    private static final String CONFIG_SERVER_HOSTNAME_1 = "http://cfg1.prod.region1:4080";
    private static final String CONFIG_SERVER_HOSTNAME_2 = "http://cfg2.prod.region1:4080";
    private static final String NODE_HOSTNAME_PREFIX = "/nodes/v2/node/";
    private static final String EXPECTED_HOSTNAME = "expected.hostname";

    @Before
    public void setup() {
        mockCommandExecutor = new MockCommandExecutor();
    }


    @Test
    public void generateNodeInfoUrl_find_config_server_test_if_url_is_formatted_correctly() throws Exception {
        mockCommandExecutor.addCommand(CAT_NODE_HOST_NAME_PATH);
        ArrayList<URL> urls = HostURLGenerator.generateNodeInfoUrl(mockCommandExecutor, CONFIG_SERVER_HOSTNAME_1 + "," + CONFIG_SERVER_HOSTNAME_2);
        String expectedUrl1 = CONFIG_SERVER_HOSTNAME_1 + NODE_HOSTNAME_PREFIX + EXPECTED_HOSTNAME;
        String expectedUrl2 = CONFIG_SERVER_HOSTNAME_2 + NODE_HOSTNAME_PREFIX + EXPECTED_HOSTNAME;
        assertEquals(expectedUrl1, urls.get(0).toString());
        assertEquals(expectedUrl2, urls.get(1).toString());
    }

    @Test
    public void generateNodeInfoURL_expected_IOException() {
        try {
            mockCommandExecutor.addCommand(CAT_WRONG_HOSTNAME_PATH);
            HostURLGenerator.generateNodeInfoUrl(mockCommandExecutor, CONFIG_SERVER_HOSTNAME_1);
            fail("Expected an IOException to be thrown");
        } catch (IOException e) {
            String expectedExceptionMessage = "Unexpected output from \"hostname\" command.";
            assertEquals(expectedExceptionMessage, e.getMessage());
        }
    }

    @Test
    public void generateNodeInfoUrl_retrieve_config_server_as_parameter_test_if_url_is_formatted_correctly() throws Exception {
        mockCommandExecutor.addCommand(CAT_NODE_HOST_NAME_PATH);
        String configServerHostname = "cfg1.prod.region1";
        ArrayList<URL> actualUrls = HostURLGenerator.generateNodeInfoUrl(mockCommandExecutor, configServerHostname);
        String expectedUrl = CONFIG_SERVER_HOSTNAME_1 + NODE_HOSTNAME_PREFIX + EXPECTED_HOSTNAME;
        String actualUrl = actualUrls.get(0).toString();
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    public void buildNodeInfoURL_should_add_protocol_and_port_in_front_when_protocol_is_absent() throws IOException {
        String configServerHostName = "www.yahoo.com";
        String nodeHostName = "index.html";
        String nodeHostnamePrefix = "/nodes/v2/node/";
        String portNumber = ":4080";
        String expectedUrl = "http://" + configServerHostName + portNumber + nodeHostnamePrefix + nodeHostName;
        assertEquals(expectedUrl, HostURLGenerator.buildNodeInfoURL(configServerHostName, nodeHostName).toString());
    }

    @Test
    public void buildNodeInfoURL_should_not_add_protocol_and_port_in_front_when_protocol_already_exists() throws IOException {
        String configServerHostName = "http://www.yahoo.com";
        String nodeHostName = "index.html";
        String nodeHostnamePrefix = "/nodes/v2/node/";
        String expectedUrl = configServerHostName + nodeHostnamePrefix + nodeHostName;
        assertEquals(expectedUrl, HostURLGenerator.buildNodeInfoURL(configServerHostName, nodeHostName).toString());
    }

}