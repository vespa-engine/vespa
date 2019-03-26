package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Test;

/**
 * @author jonmv
 */
public class BadgeApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/deployment/responses/";

    @Test
    public void testBadgeApi() {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        Application application = tester.createApplication("domain", "tenant", "application");
        ApplicationPackage packageWithService = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain"), AthenzService.from("service"))
                .region("us-west-1")
                .build();
        tester.controller().jobController().submit(application.id(),
                                                   new SourceRevision("repository", "branch", "commit"),
                                                   "foo@bar",
                                                   123,
                                                   packageWithService,
                                                   new byte[0]);

        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default"),
                              "", 302);
        tester.assertResponse(authenticatedRequest("http://localhost:8080/badge/v1/tenant/application/default/system-test?historyLength=10"),
                              "", 302);
    }

}
