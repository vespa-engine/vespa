// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
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

import javax.ws.rs.ForbiddenException;
import java.util.Collections;
import java.util.Set;

import static com.yahoo.application.container.handler.Request.Method.GET;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.application.container.handler.Request.Method.PUT;
import static com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiTest.createApplicationSubmissionData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ApplicationApiCloudTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";

    private ContainerTester tester;
    private DeploymentTester deploymentTester;

    private static final TenantName tenantName = TenantName.from("scoober");
    private static final ApplicationName applicationName = ApplicationName.from("albums");

    @Before
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        ((InMemoryFlagSource) tester.controller().flagSource())
                .withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true);
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
    public void tenant_info_workflow() {
        var infoRequest =
                request("/application/v4/tenant/scoober/info", GET)
                .roles(Set.of(Role.reader(tenantName)));
        tester.assertResponse(infoRequest, "{}", 200);

        String partialInfo = "{\"name\":\"newName\", \"billingContact\":{\"name\":\"billingName\"}}";

        var postPartial =
                request("/application/v4/tenant/scoober/info", PUT)
                        .data(partialInfo)
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(postPartial, "{\"message\":\"Tenant info updated\"}", 200);

        // Read back the updated info
        tester.assertResponse(infoRequest, "{\"name\":\"newName\",\"email\":\"\",\"website\":\"\",\"invoiceEmail\":\"\",\"contactName\":\"\",\"contactEmail\":\"\",\"billingContact\":{\"name\":\"billingName\",\"email\":\"\",\"phone\":\"\"}}", 200);

        String fullAddress = "{\"addressLines\":\"addressLines\",\"postalCodeOrZip\":\"postalCodeOrZip\",\"city\":\"city\",\"stateRegionProvince\":\"stateRegionProvince\",\"country\":\"country\"}";
        String fullBillingContact = "{\"name\":\"name\",\"email\":\"email\",\"phone\":\"phone\",\"address\":" + fullAddress + "}";
        String fullInfo = "{\"name\":\"name\",\"email\":\"email\",\"website\":\"webSite\",\"invoiceEmail\":\"invoiceEmail\",\"contactName\":\"contactName\",\"contactEmail\":\"contanctEmail\",\"address\":" + fullAddress + ",\"billingContact\":" + fullBillingContact + "}";

        // Now set all fields
        var postFull =
                request("/application/v4/tenant/scoober/info", PUT)
                        .data(fullInfo)
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(postFull, "{\"message\":\"Tenant info updated\"}", 200);

        // Now compare the updated info with the full info we sent
        tester.assertResponse(infoRequest, fullInfo, 200);
    }

    @Test
    public void trial_tenant_limit_reached() {
        ((InMemoryFlagSource) tester.controller().flagSource()).withIntFlag(Flags.MAX_TRIAL_TENANTS.id(), 1);
        tester.controller().serviceRegistry().billingController().setPlan(tenantName, PlanId.from("pay-as-you-go"), false);

        // tests that we can create the one trial tenant the flag says we can have -- and that the tenant created
        // in @Before does not count towards that limit.
        tester.controller().tenants().create(tenantSpec("tenant1"), credentials("administrator"));

        // tests that exceeding the limit throws a ForbiddenException
        try {
            tester.controller().tenants().create(tenantSpec("tenant2"), credentials("administrator"));
            fail("Should not be allowed to create tenant that exceed trial limit");
        } catch (ForbiddenException e) {
            assertEquals("Too many tenants with trial plans, please contact the Vespa support team", e.getMessage());
        }
    }

    private ApplicationPackageBuilder prodBuilder() {
        return new ApplicationPackageBuilder()
                .instances("default")
                .region("aws-us-east-1a");
    }

    private void setupTenantAndApplication() {
        var tenantSpec = new CloudTenantSpec(tenantName, "");
        tester.controller().tenants().create(tenantSpec, credentials("developer@scoober"));

        var appId = TenantAndApplicationId.from(tenantName, applicationName);
        tester.controller().applications().createApplication(appId, credentials("developer@scoober"));
    }

    private static CloudTenantSpec tenantSpec(String name) {
        return new CloudTenantSpec(TenantName.from(name), "");
    }

    private static Credentials credentials(String name) {
        return new Auth0Credentials(() -> name, Collections.emptySet());
    }
}
