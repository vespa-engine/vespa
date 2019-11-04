package com.yahoo.vespa.hosted.controller.restapi.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

import java.io.File;

/**
 * @author jonmv
 */
public class AthenzApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/athenz/responses/";

    @Test
    public void testAthenzApi() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        ((AthenzClientFactoryMock) tester.container().components().getComponent(AthenzClientFactoryMock.class.getName()))
                .getSetup().addDomain(new AthenzDbMock.Domain(new AthenzDomain("domain1")));

        // GET root
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/"),
                              new File("root.json"));

        // GET Athenz domains
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/domains"),
                              new File("athensDomain-list.json"));

        // GET properties
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/properties/"),
                              new File("property-list.json"));
    }

}

