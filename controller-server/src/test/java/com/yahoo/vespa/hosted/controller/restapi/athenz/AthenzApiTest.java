// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.athenz;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Tags;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * @author jonmv
 */
public class AthenzApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/athenz/responses/";

    @Test
    void testAthenzApi() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        ControllerTester controllerTester = new ControllerTester(tester);

        controllerTester.createTenant("sandbox", AthenzApiHandler.sandboxDomainIn(tester.controller().system()), 123L);
        controllerTester.createApplication("sandbox", "app", "default");
        tester.controller().applications().createInstance(ApplicationId.from("sandbox", "app", hostedOperator.getName()), Tags.empty());
        tester.controller().applications().createInstance(ApplicationId.from("sandbox", "app", defaultUser.getName()), Tags.empty());
        controllerTester.createApplication("sandbox", "opp", "default");

        controllerTester.createTenant("tenant1", "domain1", 123L);
        controllerTester.createApplication("tenant1", "app", "default");
        tester.athenzClientFactory().getSetup().getOrCreateDomain(new AthenzDomain("domain1")).admin(defaultUser);

        controllerTester.createTenant("tenant2", "domain2", 123L);
        controllerTester.createApplication("tenant2", "app", "default");

        // GET root
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/"),
                new File("root.json"));

        // GET Athenz domains
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/domains"),
                new File("athensDomain-list.json"));

        // GET properties
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/properties/"),
                new File("property-list.json"));

        // POST user signup
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/user", "", Request.Method.POST),
                "{\"message\":\"User 'bob' added to admin role of 'vespa.vespa.tenants.sandbox'\"}");
    }

}

