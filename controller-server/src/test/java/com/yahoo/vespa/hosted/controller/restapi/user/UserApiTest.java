package com.yahoo.vespa.hosted.controller.restapi.user;

import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

/**
 * @author jonmv
 */
public class UserApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/user/responses/";

    @Test
    public void testBadgeApi() {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);

        tester.assertResponse(authenticatedRequest("http://localhost:8080/user/v1/"),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"No 'GET' handler at '/user/v1/'\"}", 404);
    }

}
