package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.CloudAccount;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Test of the node repository REST API, for enclaves. The setup in this class is duplicated from NodesV2ApiTest,
 * but it is inconvenient to reuse that class because we need to emulate an internal cloud account.
 *
 * @author gjoranv
 */
public class NodesV2ApiEnclaveTest {

    private RestApiTester tester;

    @Before
    public void createTester() {
        tester = new RestApiTester(CloudAccount.from("111222333444"));
    }

    @After
    public void closeTester() {
        tester.close();
    }

    @Test
    public void returns_only_enclave_nodes_when_enclave_param_is_set() throws IOException {
        tester.assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&enclave=true"), "enclave-nodes.json");

    }
}
