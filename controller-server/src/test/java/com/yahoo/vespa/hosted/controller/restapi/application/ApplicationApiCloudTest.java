// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.controller.api.integration.user.User;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.security.Auth0Credentials;
import com.yahoo.vespa.hosted.controller.security.CloudTenantSpec;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static com.yahoo.application.container.handler.Request.Method.GET;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiTest.createApplicationSubmissionData;

public class ApplicationApiCloudTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";

    private ContainerTester tester;
    private DeploymentTester deploymentTester;

    private static final TenantName tenantName = TenantName.from("scoober");
    private static final ApplicationName applicationName = ApplicationName.from("albums");
    private static final User developerUser = new User("developer", "Joe Developer", "", "");

    @Before
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        ((InMemoryFlagSource) tester.controller().flagSource())
                .withBooleanFlag(Flags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true);
        deploymentTester = new DeploymentTester(new ControllerTester(tester));
        deploymentTester.controllerTester().computeVersionStatus();
        setupTenantAndApplication();
    }

    @Test
    public void test_missing_security_clients_pem() {
        var application = prodBuilder().build();

        var deployRequest = request("/application/v4/tenant/scoober/application/albums/submit", POST)
                .data(createApplicationSubmissionData(application, 0))
                .roles(Set.of(Role.developer(tenantName)));

        tester.assertResponse(
                deployRequest,
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Missing required file 'security/clients.pem'\"}",
                400);
    }

    @Test
    public void get_empty_tenant_info() {
        var infoRequest =
                request("/application/v4/tenant/scoober/info", GET)
                .roles(Set.of(Role.reader(tenantName)));
        tester.assertResponse(infoRequest, "{}", 200);
    }

    @Test
    public void post_partial_tenant_info() {
        var infoRequest =
                request("/application/v4/tenant/scoober/info", POST)
                        .data("{\"name\":\"newName\", \"billingContact\":{\"name\":\"billingName\"}}")
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(infoRequest, "{}", 200);
    }

    private ApplicationPackageBuilder prodBuilder() {
        return new ApplicationPackageBuilder()
                .instances("default")
                .environment(Environment.prod)
                .region("aws-us-east-1a");
    }

    private void setupTenantAndApplication() {
        var tenantSpec = new CloudTenantSpec(tenantName, "");
        tester.controller().tenants().create(tenantSpec, credentials("developer@scoober"));

        var appId = TenantAndApplicationId.from(tenantName, applicationName);
        tester.controller().applications().createApplication(appId, credentials("developer@scoober"));
    }

    private static Credentials credentials(String name) {
        return new Auth0Credentials(() -> name, Collections.emptySet());
    }
}
