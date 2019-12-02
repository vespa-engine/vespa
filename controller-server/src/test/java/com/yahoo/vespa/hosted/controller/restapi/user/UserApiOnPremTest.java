package com.yahoo.vespa.hosted.controller.restapi.user;

import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.user.User;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

import java.io.File;
import java.util.stream.Stream;

/**
 * @author freva
 */
public class UserApiOnPremTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/user/responses/";

    @Test
    public void userMetadataOnPremTest() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        ControllerTester controller = new ControllerTester(tester);
        User user = new User("dev@domail", "Joe Developer", "dev", null);

        controller.createTenant("tenant1", "domain1", 1L);
        controller.createApplication("tenant1", "app1", "default");
        controller.createApplication("tenant1", "app2", "default");
        controller.createApplication("tenant1", "app2", "myinstance");
        controller.createApplication("tenant1", "app3");

        controller.createTenant("tenant2", "domain2", 2L);
        controller.createApplication("tenant2", "app2", "test");

        controller.createTenant("tenant3", "domain3", 3L);
        controller.createApplication("tenant3", "app1");

        controller.createTenant("sandbox", "domain4", 4L);
        controller.createApplication("sandbox", "app1", "default");
        controller.createApplication("sandbox", "app2", "default");
        controller.createApplication("sandbox", "app2", "dev");

        AthenzIdentity operator = AthenzIdentities.from("vespa.alice");
        controller.athenzDb().addHostedOperator(operator);
        AthenzIdentity tenantAdmin = AthenzIdentities.from("domain1.bob");
        Stream.of("domain1", "domain2", "domain4")
                .map(AthenzDomain::new)
                .map(controller.athenzDb()::getOrCreateDomain)
                .forEach(d -> d.admin(AthenzIdentities.from("domain1.bob")));

        tester.assertResponse(createUserRequest(user, operator),
                new File("user-without-applications.json"));

        tester.assertResponse(createUserRequest(user, tenantAdmin),
                new File("user-with-applications-athenz.json"));
    }

    private Request createUserRequest(User user, AthenzIdentity identity) {
        Request request = new Request("http://localhost:8080/api/user/v1/user");
        request.getAttributes().put(User.ATTRIBUTE_NAME, user);
        return addIdentityToRequest(request, identity);
    }
}
