// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.user;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.MockBillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.jdisc.http.filter.security.misc.User;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static com.yahoo.application.container.handler.Request.Method.DELETE;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.application.container.handler.Request.Method.PUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
public class UserApiTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/user/responses/";
    private static final String pemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                               "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                               "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                               "-----END PUBLIC KEY-----\n";
    private static final String otherPemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFELzPyinTfQ/sZnTmRp5E4Ve/sbE\n" +
                                                    "pDhJeqczkyFcT2PysJ5sZwm7rKPEeXDOhzTPCyRvbUqc2SGdWbKUGGa/Yw==\n" +
                                                    "-----END PUBLIC KEY-----\n";
    private static final String quotedPemPublicKey = pemPublicKey.replaceAll("\\n", "\\\\n");


    @Test
    void testUserManagement() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());
        Set<Role> operator = Set.of(Role.hostedOperator());
        ApplicationId id = ApplicationId.from("my-tenant", "my-app", "default");


        // GET at application/v4 root fails as it's not public read.
        tester.assertResponse(request("/application/v4/"),
                accessDenied, 403);

        // GET at application/v4/tenant succeeds for operators.
        tester.assertResponse(request("/application/v4/tenant")
                        .roles(operator),
                "[]");

        // POST a tenant is not available to everyone.
        tester.assertResponse(request("/application/v4/tenant/my-tenant", POST)
                        .data("{\"token\":\"hello\"}"),
                "{\"error-code\":\"FORBIDDEN\",\"message\":\"You are not currently permitted to create tenants. Please contact the Vespa team to request access.\"}", 403);

        // POST a tenant is available to operators.
        tester.assertResponse(request("/application/v4/tenant/my-tenant", POST)
                        .roles(operator)
                        .principal("administrator@tenant")
                        .user(new User("administrator@tenant", "administrator", "admin", "picture"))
                        .data("{\"token\":\"hello\"}"),
                new File("tenant-without-applications.json"));

        // GET at tenant/info with contact information.
        tester.assertResponse(request("/application/v4/tenant/my-tenant/info")
                        .roles(operator)
                        .principal("administrator@tenant"),
                new File("tenant-info-after-created.json"));

        // GET at user/v1 root fails as no access control is defined there.
        tester.assertResponse(request("/user/v1/"),
                accessDenied, 403);

        // POST a hosted operator role is not allowed.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"evil@evil\",\"roles\":[\"hostedOperator\"]}"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Malformed or illegal role name 'hostedOperator'.\"}", 400);

        // POST a tenant developer is available to the tenant owner.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"developer@tenant\",\"roles\":[\"developer\",\"reader\"]}"),
                "{\"message\":\"user 'developer@tenant' is now a member of role 'developer' of 'my-tenant', role 'reader' of 'my-tenant'\"}");

        // POST a tenant admin is not available to a tenant developer.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                        .roles(Set.of(Role.developer(id.tenant())))
                        .data("{\"user\":\"developer@tenant\",\"roles\":[\"administrator\"]}"),
                accessDenied, 403);

        // POST an application is allowed for a tenant developer.
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app", POST)
                        .principal("developer@tenant")
                        .roles(Set.of(Role.developer(id.tenant()))),
                new File("application-created.json"));

        // POST an application is not allowed under a different tenant.
        tester.assertResponse(request("/application/v4/tenant/other-tenant/application/my-app", POST)
                        .roles(Set.of(Role.administrator(id.tenant()))),
                accessDenied, 403);

        // GET tenant role information is available to readers.
        tester.assertResponse(request("/user/v1/tenant/my-tenant")
                        .roles(Set.of(Role.reader(id.tenant()))),
                new File("tenant-roles.json"));

        // POST a pem deploy key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app/key", POST)
                        .roles(Set.of(Role.developer(id.tenant())))
                        .data("{\"key\":\"" + pemPublicKey + "\"}"),
                new File("first-deploy-key.json"));

        // POST a pem developer key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", POST)
                        .principal("joe@dev")
                        .roles(Set.of(Role.developer(id.tenant())))
                        .data("{\"key\":\"" + pemPublicKey + "\"}"),
                new File("first-developer-key.json"));

        // POST the same pem developer key for a different user is forbidden
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", POST)
                        .principal("operator@tenant")
                        .roles(Set.of(Role.developer(id.tenant())))
                        .data("{\"key\":\"" + pemPublicKey + "\"}"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Key " +  quotedPemPublicKey + " is already owned by joe@dev\"}",
                400);

        // POST a different developer key for an existing user is forbidden
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", POST)
                                      .principal("joe@dev")
                                      .roles(Set.of(Role.developer(id.tenant())))
                                      .data("{\"key\":\"" + otherPemPublicKey + "\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"joe@dev is already associated with key " + quotedPemPublicKey + "\"}",
                              400);

        // POST in a different pem developer key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", POST)
                        .principal("developer@tenant")
                        .roles(Set.of(Role.developer(id.tenant())))
                        .data("{\"key\":\"" + otherPemPublicKey + "\"}"),
                new File("both-developer-keys.json"));

        // GET tenant information with keys
        tester.assertResponse(request("/application/v4/tenant/my-tenant/")
                        .roles(Set.of(Role.reader(id.tenant()))),
                new File("tenant-with-keys.json"));

        // DELETE a pem developer key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", DELETE)
                        .roles(Set.of(Role.developer(id.tenant())))
                        .data("{\"key\":\"" + pemPublicKey + "\"}"),
                new File("second-developer-key.json"));

        // PUT in a new secret store for the tenant
        tester.assertResponse(request("/application/v4/tenant/my-tenant/secret-store/secret-foo", PUT)
                        .principal("developer@tenant")
                        .roles(Set.of(Role.developer(id.tenant())))
                        .data("{\"awsId\":\"123\",\"role\":\"secret-role\",\"externalId\":\"abc\"}"),
                "{\"secretStores\":[{\"name\":\"secret-foo\",\"awsId\":\"123\",\"role\":\"secret-role\"}]}",
                200);

        // GET a tenant with secret stores configured
        tester.assertResponse(request("/application/v4/tenant/my-tenant")
                        .principal("reader@tenant")
                        .roles(Set.of(Role.reader(id.tenant()))),
                new File("tenant-with-secrets.json"));

        // DELETE an application is available to developers.
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app", DELETE)
                        .roles(Set.of(Role.developer(id.tenant()))),
                "{\"message\":\"Deleted application my-tenant.my-app\"}");

        // DELETE a tenant role is available to tenant admins.
        // DELETE the developer role clears any developer key.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", DELETE)
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"developer@tenant\",\"roles\":[\"developer\",\"reader\"]}"),
                "{\"message\":\"user 'developer@tenant' is no longer a member of role 'developer' of 'my-tenant', role 'reader' of 'my-tenant'\"}");

        // DELETE the last tenant owner is not allowed.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", DELETE)
                        .roles(operator)
                        .data("{\"user\":\"administrator@tenant\",\"roles\":[\"administrator\"]}"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can't remove the last administrator of a tenant.\"}", 400);

        // DELETE the tenant is not allowed
        tester.assertResponse(request("/application/v4/tenant/my-tenant", DELETE)
                        .roles(Set.of(Role.developer(id.tenant()))),
                "{\n" +
                        "  \"code\" : 403,\n" +
                        "  \"message\" : \"Access denied\"\n" +
                        "}", 403);
    }

    @Test
    void userMetadataTest() {
        try (Flags.Replacer ignored = Flags.clearFlagsForTesting(PermanentFlags.MAX_TRIAL_TENANTS.id(), PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id())) {
            ContainerTester tester = new ContainerTester(container, responseFiles);
            ((InMemoryFlagSource) tester.controller().flagSource())
                    .withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true);
            ControllerTester controller = new ControllerTester(tester);
            Set<Role> operator = Set.of(Role.hostedOperator(), Role.hostedSupporter(), Role.hostedAccountant());
            User user = new User("dev@domail", "Joe Developer", "dev", null);

            tester.assertResponse(request("/user/v1/user")
                            .roles(operator)
                            .user(user),
                    new File("user-without-applications.json"));

            controller.createTenant("tenant1", Tenant.Type.cloud);
            controller.createApplication("tenant1", "app1", "default");
            controller.createApplication("tenant1", "app2", "default");
            controller.createApplication("tenant1", "app2", "myinstance");
            controller.createApplication("tenant1", "app3");

            controller.createTenant("tenant2", Tenant.Type.cloud);
            controller.createApplication("tenant2", "app2", "test");

            controller.createTenant("tenant3", Tenant.Type.cloud);
            controller.createApplication("tenant3", "app1");

            controller.createTenant("sandbox", Tenant.Type.cloud);
            controller.createApplication("sandbox", "app1", "default");
            controller.createApplication("sandbox", "app2", "default");
            controller.createApplication("sandbox", "app2", "dev");

            // Should still be empty because none of the roles explicitly refer to any of the applications
            tester.assertResponse(request("/user/v1/user")
                            .roles(operator)
                            .user(user),
                    new File("user-without-applications.json"));

            // Empty applications because tenant dummy does not exist
            tester.assertResponse(request("/user/v1/user")
                            .roles(Set.of(Role.administrator(TenantName.from("tenant1")),
                                    Role.developer(TenantName.from("tenant2")),
                                    Role.developer(TenantName.from("sandbox")),
                                    Role.reader(TenantName.from("sandbox"))))
                            .user(user),
                    new File("user-with-applications-cloud.json"));
        }
    }

    @Test
    void findUser() {
        try (Flags.Replacer ignored = Flags.clearFlagsForTesting(PermanentFlags.MAX_TRIAL_TENANTS.id(), PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id())) {
            ContainerTester tester = new ContainerTester(container, responseFiles);
            ((InMemoryFlagSource) tester.controller().flagSource())
                    .withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true);
            Set<Role> operator = Set.of(Role.hostedOperator(), Role.hostedSupporter(), Role.hostedAccountant());
            User user = new User("dev@domail", "Joe Developer", "dev", null);
            tester.controller().serviceRegistry().billingController().updateCache(List.of());

            Role developer = Role.developer(TenantName.from("scoober"));
            tester.userManagement().createRole(developer);
            tester.userManagement().addToRoles(new UserId("dev@domail"), Set.of(developer));

            tester.assertResponse(request("/user/v1/find?email=dev@domail")
                    .roles(operator)
                    .user(user),
                    """
                            {"users":[{"isPublic":true,"isCd":false,"hasTrialCapacity":true,"user":{"name":"dev@domail","email":"dev@domail","verified":false},"tenants":{"scoober":{"supported":false,"roles":["developer"]}},"flags":[{"id":"enable-public-signup-flow","rules":[{"value":false}]}]}]}""");

            tester.assertResponse(request("/user/v1/find?query=email:dev@domail")
                    .roles(operator)
                    .user(user),
                    """
                            {"users":[]}""");
        }
    }

    @Test
    void maxTrialTenants() {
        try (Flags.Replacer ignored = Flags.clearFlagsForTesting(PermanentFlags.MAX_TRIAL_TENANTS.id(), PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id())) {
            ContainerTester tester = new ContainerTester(container, responseFiles);
            ((InMemoryFlagSource) tester.controller().flagSource())
                    .withIntFlag(PermanentFlags.MAX_TRIAL_TENANTS.id(), 1)
                    .withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true);
            ControllerTester controller = new ControllerTester(tester);
            User user = new User("dev@domail", "Joe Developer", "dev", null);

            controller.createTenant("tenant1", Tenant.Type.cloud);
            ((MockBillingController) controller.serviceRegistry().billingController()).setTenants(controller.controller().tenants().asList().stream().map(Tenant::name).toList());

            tester.assertResponse(
                    request("/user/v1/user").user(user),
                    new File("user-without-trial-capacity-cloud.json"));
        }
    }

    @Test
    void supportTenant() {
        try (Flags.Replacer ignored = Flags.clearFlagsForTesting(PermanentFlags.MAX_TRIAL_TENANTS.id(), PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id())) {
            ContainerTester tester = new ContainerTester(container, responseFiles);
            ((InMemoryFlagSource) tester.controller().flagSource())
                    .withIntFlag(PermanentFlags.MAX_TRIAL_TENANTS.id(), 10)
                    .withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true);
            ControllerTester controller = new ControllerTester(tester);
            User user = new User("dev@domail", "Joe Developer", "dev", null);

            var tenant1 = controller.createTenant("tenant1", Tenant.Type.cloud);
            var tenant2 = controller.createTenant("tenant2", Tenant.Type.cloud);
            controller.serviceRegistry().billingController().setPlan(tenant2, PlanId.from("paid"), false, false);

            tester.assertResponse(
                    request("/user/v1/user")
                            .roles(Role.reader(tenant1), Role.reader(tenant2))
                            .user(user),
                    new File("user-with-supported-tenant.json"));
        }

    }

    @Test
    public void verifyMail() {
        var tester = new ContainerTester(container, responseFiles);
        var controller = new ControllerTester(tester);
        controller.createTenant("tenant1", Tenant.Type.cloud);
        var pendingMailVerification = tester.controller().mailVerifier().sendMailVerification(TenantName.from("tenant1"), "foo@bar.com", PendingMailVerification.MailType.NOTIFICATIONS);

        tester.assertResponse(request("/user/v1/email/verify", POST)
                        .data("{\"verificationCode\":\"blurb\"}"),
                "{\"error-code\":\"NOT_FOUND\",\"message\":\"No pending email verification with code blurb found\"}", 404);

        tester.assertResponse(request("/user/v1/email/verify", POST)
                        .data("{\"verificationCode\":\"" + pendingMailVerification.getVerificationCode() + "\"}"),
                "{\"message\":\"Email with verification code " + pendingMailVerification.getVerificationCode() + " has been verified\"}", 200);
    }
}
