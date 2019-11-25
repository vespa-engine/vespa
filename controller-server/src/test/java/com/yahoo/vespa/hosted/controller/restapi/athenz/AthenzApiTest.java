package com.yahoo.vespa.hosted.controller.restapi.athenz;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.ControllerTester;
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
        ControllerTester controllerTester = new ControllerTester(tester);

        TenantName sandbox = controllerTester.createTenant("sandbox", AthenzApiHandler.sandboxDomainIn(tester.controller().system()), 123L);
        controllerTester.createApplication(sandbox, "app", "default");
        tester.controller().applications().createInstance(ApplicationId.from("sandbox", "app", hostedOperator.getName()));
        tester.controller().applications().createInstance(ApplicationId.from("sandbox", "app", defaultUser.getName()));
        controllerTester.createApplication(sandbox, "opp", "default");

        TenantName tenant1 = controllerTester.createTenant("tenant1", "domain1", 123L);
        controllerTester.createApplication(tenant1, "app", "default");
        tester.athenzClientFactory().getSetup().getOrCreateDomain(new AthenzDomain("domain1")).admin(defaultUser);

        TenantName tenant2 = controllerTester.createTenant("tenant2", "domain2", 123L);
        controllerTester.createApplication(tenant2, "app", "default");

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

        // GET accessible instances
        tester.assertResponse(authenticatedRequest("http://localhost:8080/athenz/v1/user"),
                              new File("accessible-instances.json"));

    }

}

