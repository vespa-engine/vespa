// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import ai.vespa.hosted.api.MultiPartStreamer;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.restapi.RestApiException;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.security.Auth0Credentials;
import com.yahoo.vespa.hosted.controller.security.CloudTenantSpec;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.application.container.handler.Request.Method.DELETE;
import static com.yahoo.application.container.handler.Request.Method.GET;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.application.container.handler.Request.Method.PUT;
import static com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiTest.createApplicationSubmissionData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author oyving
 */
public class ApplicationApiCloudTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";

    private ContainerTester tester;

    private static final TenantName tenantName = TenantName.from("scoober");
    private static final ApplicationName applicationName = ApplicationName.from("albums");

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        ((InMemoryFlagSource) tester.controller().flagSource())
                .withBooleanFlag(PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.id(), true);
        setupTenantAndApplication();
    }

    @Test
    void tenant_info_profile() {
        var request = request("/application/v4/tenant/scoober/info/profile", GET)
                .roles(Set.of(Role.reader(tenantName)));
        tester.assertResponse(request, "{}", 200);

        var updateRequest = request("/application/v4/tenant/scoober/info/profile", PUT)
                .data("{\"contact\":{\"name\":\"Some Name\",\"email\":\"foo@example.com\"},\"tenant\":{\"company\":\"Scoober, Inc.\",\"website\":\"https://example.com/\"}}")
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(updateRequest, "{\"message\":\"Tenant info updated\"}", 200);

        tester.assertResponse(request, "{\"contact\":{\"name\":\"Some Name\",\"email\":\"foo@example.com\",\"emailVerified\":false},\"tenant\":{\"company\":\"\",\"website\":\"https://example.com/\"}}", 200);
    }

    @Test
    void tenant_info_billing() {
        var request = request("/application/v4/tenant/scoober/info/billing", GET)
                .roles(Set.of(Role.reader(tenantName)));
        tester.assertResponse(request, "{}", 200);

        var fullAddress = "{\"addressLines\":\"addressLines\",\"postalCodeOrZip\":\"postalCodeOrZip\",\"city\":\"city\",\"stateRegionProvince\":\"stateRegionProvince\",\"country\":\"country\"}";
        var fullBillingContact = "{\"contact\":{\"name\":\"name\",\"email\":\"foo@example\",\"phone\":\"phone\"},\"address\":" + fullAddress + "}";

        var updateRequest = request("/application/v4/tenant/scoober/info/billing", PUT)
                .data(fullBillingContact)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(updateRequest, "{\"message\":\"Tenant info updated\"}", 200);

        tester.assertResponse(request, "{\"contact\":{\"name\":\"name\",\"email\":\"foo@example\",\"phone\":\"phone\"},\"address\":{\"addressLines\":\"addressLines\",\"postalCodeOrZip\":\"postalCodeOrZip\",\"city\":\"city\",\"stateRegionProvince\":\"stateRegionProvince\",\"country\":\"country\"}}", 200);
    }

    @Test
    void tenant_info_contacts() {
        var request = request("/application/v4/tenant/scoober/info/contacts", GET)
                .roles(Set.of(Role.reader(tenantName)));
        tester.assertResponse(request, "{\"contacts\":[]}", 200);


        var fullContacts = "{\"contacts\":[{\"audiences\":[\"tenant\"],\"email\":\"contact1@example.com\",\"emailVerified\":false},{\"audiences\":[\"notifications\"],\"email\":\"contact2@example.com\",\"emailVerified\":false},{\"audiences\":[\"tenant\",\"notifications\"],\"email\":\"contact3@example.com\",\"emailVerified\":false}]}";
        var updateRequest = request("/application/v4/tenant/scoober/info/contacts", PUT)
                .data(fullContacts)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(updateRequest, "{\"message\":\"Tenant info updated\"}", 200);
        tester.assertResponse(request, fullContacts, 200);
    }

    @Test
    void tenant_info_workflow() {
        var infoRequest =
                request("/application/v4/tenant/scoober/info", GET)
                        .roles(Set.of(Role.reader(tenantName)));
        tester.assertResponse(infoRequest, "{}", 200);

        String partialInfo = "{\"contactName\":\"newName\", \"contactEmail\": \"foo@example.com\", \"billingContact\":{\"name\":\"billingName\"}}";
        var postPartial =
                request("/application/v4/tenant/scoober/info", PUT)
                        .data(partialInfo)
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(postPartial, "{\"message\":\"Tenant info updated\"}", 200);

        String partialContacts = "{\"contacts\": [{\"audiences\": [\"tenant\"],\"email\": \"contact1@example.com\"}]}";
        var postPartialContacts =
                request("/application/v4/tenant/scoober/info", PUT)
                        .data(partialContacts)
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(postPartialContacts, "{\"message\":\"Tenant info updated\"}", 200);

        // Read back the updated info
        tester.assertResponse(infoRequest, "{\"name\":\"\",\"email\":\"\",\"website\":\"\",\"contactName\":\"newName\",\"contactEmail\":\"foo@example.com\",\"contactEmailVerified\":false,\"billingContact\":{\"name\":\"billingName\",\"email\":\"\",\"phone\":\"\"},\"contacts\":[{\"audiences\":[\"tenant\"],\"email\":\"contact1@example.com\",\"emailVerified\":false}]}", 200);

        String fullAddress = "{\"addressLines\":\"addressLines\",\"postalCodeOrZip\":\"postalCodeOrZip\",\"city\":\"city\",\"stateRegionProvince\":\"stateRegionProvince\",\"country\":\"country\"}";
        String fullBillingContact = "{\"name\":\"name\",\"email\":\"foo@example\",\"phone\":\"phone\",\"address\":" + fullAddress + "}";
        String fullContacts = "[{\"audiences\":[\"tenant\"],\"email\":\"contact1@example.com\",\"emailVerified\":false},{\"audiences\":[\"notifications\"],\"email\":\"contact2@example.com\",\"emailVerified\":false},{\"audiences\":[\"tenant\",\"notifications\"],\"email\":\"contact3@example.com\",\"emailVerified\":false}]";
        String fullInfo = "{\"name\":\"name\",\"email\":\"foo@example\",\"website\":\"https://yahoo.com\",\"contactName\":\"contactName\",\"contactEmail\":\"contact@example.com\",\"contactEmailVerified\":false,\"address\":" + fullAddress + ",\"billingContact\":" + fullBillingContact + ",\"contacts\":" + fullContacts + "}";

        // Now set all fields
        var postFull =
                request("/application/v4/tenant/scoober/info", PUT)
                        .data(fullInfo)
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(postFull, "{\"message\":\"Tenant info updated\"}", 200);

        // Now compare the updated info with the full info we sent
        tester.assertResponse(infoRequest, fullInfo, 200);

        var invalidBody = "{\"mail\":\"contact1@example.com\", \"mailType\":\"blurb\"}";
        var resendMailRequest =
                request("/application/v4/tenant/scoober/info/resend-mail-verification", PUT)
                        .data(invalidBody)
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(resendMailRequest, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown mail type blurb\"}", 400);

        var resendMailBody = "{\"mail\":\"contact2@example.com\", \"mailType\":\"notifications\"}";
        resendMailRequest =
                request("/application/v4/tenant/scoober/info/resend-mail-verification", PUT)
                        .data(resendMailBody)
                        .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(resendMailRequest, "{\"message\":\"Re-sent verification mail to contact2@example.com\"}", 200);
    }

    @Test
    void tenant_info_missing_fields() {
        // tenants can be created with empty tenant info - they're not part of the POST to v4/tenant
        var infoRequest =
                request("/application/v4/tenant/scoober/info", GET)
                        .roles(Set.of(Role.reader(tenantName)));
        tester.assertResponse(infoRequest, "{}", 200);

        // name needs to be present and not blank
        var partialInfoMissingName = "{\"contactName\": \" \"}";
        var missingNameResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(partialInfoMissingName)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(missingNameResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"'contactName' cannot be empty\"}", 400);

        // email needs to be present, not blank, and contain an @
        var partialInfoMissingEmail = "{\"contactName\": \"Scoober Rentals Inc.\", \"contactEmail\": \" \"}";
        var missingEmailResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(partialInfoMissingEmail)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(missingEmailResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid email address\"}", 400);

        var partialInfoBadEmail = "{\"contactName\": \"Scoober Rentals Inc.\", \"contactEmail\": \"somethingweird\"}";
        var badEmailResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(partialInfoBadEmail)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(badEmailResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid email address\"}", 400);

        var invalidWebsite = "{\"contactName\": \"Scoober Rentals Inc.\", \"contactEmail\": \"email@scoober.com\", \"website\": \"scoober\" }";
        var badWebsiteResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(invalidWebsite)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(badWebsiteResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"'website' needs to be a valid address\"}", 400);

        // If any of the address field is set, all fields in address need to be present
        var addressInfo = "{\n" +
                "  \"name\": \"Vespa User\",\n" +
                "  \"email\": \"user@yahooinc.com\",\n" +
                "  \"website\": \"\",\n" +
                "  \"contactName\": \"Vespa User\",\n" +
                "  \"contactEmail\": \"user@yahooinc.com\",\n" +
                "  \"address\": {\n" +
                "    \"addressLines\": \"\",\n" +
                "    \"postalCodeOrZip\": \"7018\",\n" +
                "    \"city\": \"\",\n" +
                "    \"stateRegionProvince\": \"\",\n" +
                "    \"country\": \"\"\n" +
                "  }\n" +
                "}";
        var addressInfoResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(addressInfo)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(addressInfoResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"All address fields must be set\"}", 400);

        // at least one notification activity must be enabled
        var contactsWithoutAudience = "{\"contacts\": [{\"email\": \"contact1@example.com\"}]}";
        var contactsWithoutAudienceResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(contactsWithoutAudience)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(contactsWithoutAudienceResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"At least one notification activity must be enabled\"}", 400);

        // email needs to be present, not blank, and contain an @
        var contactsWithInvalidEmail = "{\"contacts\": [{\"audiences\": [\"tenant\"],\"email\": \"contact1\"}]}";
        var contactsWithInvalidEmailResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(contactsWithInvalidEmail)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(contactsWithInvalidEmailResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid email address\"}", 400);

        // duplicate contact is not allowed
        var contactsWithDuplicateEmail = "{\"contacts\": [{\"audiences\": [\"tenant\"],\"email\": \"contact1@email.com\"}, {\"audiences\": [\"tenant\"],\"email\": \"contact1@email.com\"}]}";
        var contactsWithDuplicateEmailResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(contactsWithDuplicateEmail)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(contactsWithDuplicateEmailResponse, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Duplicate contact: email 'contact1@email.com'\"}", 400);

        // updating a tenant that already has the fields set works
        var basicInfo = "{\"contactName\": \"Scoober Rentals Inc.\", \"contactEmail\": \"foo@example.com\"}";
        var basicInfoResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(basicInfo)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(basicInfoResponse, "{\"message\":\"Tenant info updated\"}", 200);

        var otherInfo = "{\"billingContact\":{\"name\":\"billingName\"}}";
        var otherInfoResponse = request("/application/v4/tenant/scoober/info", PUT)
                .data(otherInfo)
                .roles(Set.of(Role.administrator(tenantName)));
        tester.assertResponse(otherInfoResponse, "{\"message\":\"Tenant info updated\"}", 200);
    }

    @Test
    void trial_tenant_limit_reached() {
        ((InMemoryFlagSource) tester.controller().flagSource()).withIntFlag(PermanentFlags.MAX_TRIAL_TENANTS.id(), 1);
        tester.controller().serviceRegistry().billingController().setPlan(tenantName, PlanId.from("pay-as-you-go"), false, false);

        // tests that we can create the one trial tenant the flag says we can have -- and that the tenant created
        // in @Before does not count towards that limit.
        tester.controller().tenants().create(tenantSpec("tenant1"), credentials("administrator"));

        // tests that exceeding the limit throws a ForbiddenException
        try {
            tester.controller().tenants().create(tenantSpec("tenant2"), credentials("administrator"));
            fail("Should not be allowed to create tenant that exceed trial limit");
        } catch (RestApiException.Forbidden e) {
            assertEquals("Too many tenants with trial plans, please contact the Vespa support team", e.getMessage());
        }
    }

    @Test
    void test_secret_store_configuration() {
        var secretStoreRequest =
                request("/application/v4/tenant/scoober/secret-store/some-name", PUT)
                        .data("{" +
                                "\"awsId\": \"123\"," +
                                "\"role\": \"role-id\"," +
                                "\"externalId\": \"321\"" +
                                "}")
                        .roles(Set.of(Role.developer(tenantName)));
        tester.assertResponse(secretStoreRequest, "{\"secretStores\":[{\"name\":\"some-name\",\"awsId\":\"123\",\"role\":\"role-id\"}]}", 200);
        tester.assertResponse(secretStoreRequest, "{" +
                "\"error-code\":\"BAD_REQUEST\"," +
                "\"message\":\"Secret store TenantSecretStore{name='some-name', awsId='123', role='role-id'} is already configured\"" +
                "}", 400);

        secretStoreRequest =
                request("/application/v4/tenant/scoober/secret-store/should-fail", PUT)
                        .data("{" +
                                "\"awsId\": \" \"," +
                                "\"role\": \"role-id\"," +
                                "\"externalId\": \"321\"" +
                                "}")
                        .roles(Set.of(Role.developer(tenantName)));
        tester.assertResponse(secretStoreRequest, "{" +
                "\"error-code\":\"BAD_REQUEST\"," +
                "\"message\":\"Secret store TenantSecretStore{name='should-fail', awsId=' ', role='role-id'} is invalid\"" +
                "}", 400);
    }

    @Test
    void validate_secret_store() {
        deployApplication();
        var secretStoreRequest =
                request("/application/v4/tenant/scoober/secret-store/secret-foo/validate?aws-region=us-west-1&parameter-name=foo&application-id=scoober.albums.default&zone=prod.aws-us-east-1c", GET)
                        .roles(Set.of(Role.developer(tenantName)));
        tester.assertResponse(secretStoreRequest, "{" +
                "\"error-code\":\"NOT_FOUND\"," +
                "\"message\":\"No secret store 'secret-foo' configured for tenant 'scoober'\"" +
                "}", 404);

        tester.controller().tenants().lockOrThrow(tenantName, LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withSecretStore(new TenantSecretStore("secret-foo", "123", "some-role"));
            tester.controller().tenants().store(lockedTenant);
        });

        // ConfigServerMock returns message on format deployment.toString() + " - " + tenantSecretStore.toString()
        secretStoreRequest =
                request("/application/v4/tenant/scoober/secret-store/secret-foo/validate?aws-region=us-west-1&parameter-name=foo&application-id=scoober.albums.default&zone=prod.aws-us-east-1c", GET)
                        .roles(Set.of(Role.developer(tenantName)));
        tester.assertResponse(secretStoreRequest, "{\"target\":\"scoober.albums in prod.aws-us-east-1c\",\"result\":{\"settings\":{\"name\":\"foo\",\"role\":\"vespa-secretstore-access\",\"awsId\":\"892075328880\",\"externalId\":\"*****\",\"region\":\"us-east-1\"},\"status\":\"ok\"}}", 200);

        secretStoreRequest =
                request("/application/v4/tenant/scoober/secret-store/secret-foo/validate?aws-region=us-west-1&parameter-name=foo&application-id=scober.albums.default&zone=prod.aws-us-east-1c", GET)
                        .roles(Set.of(Role.developer(tenantName)));
        tester.assertResponse(secretStoreRequest, "{" +
                "\"error-code\":\"BAD_REQUEST\"," +
                "\"message\":\"Invalid application id\"" +
                "}", 400);
    }

    @Test
    void delete_secret_store() {
        var deleteRequest =
                request("/application/v4/tenant/scoober/secret-store/secret-foo", DELETE)
                        .roles(Set.of(Role.developer(tenantName)));
        tester.assertResponse(deleteRequest, "{" +
                "\"error-code\":\"NOT_FOUND\"," +
                "\"message\":\"Could not delete secret store 'secret-foo': Secret store not found\"" +
                "}", 404);

        tester.controller().tenants().lockOrThrow(tenantName, LockedTenant.Cloud.class, lockedTenant -> {
            lockedTenant = lockedTenant.withSecretStore(new TenantSecretStore("secret-foo", "123", "some-role"));
            tester.controller().tenants().store(lockedTenant);
        });
        var tenant = tester.controller().tenants().require(tenantName, CloudTenant.class);
        assertEquals(1, tenant.tenantSecretStores().size());
        tester.assertResponse(deleteRequest, "{\"secretStores\":[]}", 200);
        tenant = tester.controller().tenants().require(tenantName, CloudTenant.class);
        assertEquals(0, tenant.tenantSecretStores().size());
    }

    @Test
    void archive_uri_test() {
        ControllerTester wrapped = new ControllerTester(tester);
        wrapped.upgradeSystem(Version.fromString("7.1"));
        new DeploymentTester(wrapped).newDeploymentContext(ApplicationId.from(tenantName, applicationName, InstanceName.defaultName()))
                                     .submit()
                                     .deploy();

        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertFalse(response.getBodyAsString().contains("archiveAccessRole")),
                200);

        tester.assertResponse(request("/application/v4/tenant/scoober/archive-access/aws", PUT)
                        .data("{\"role\":\"arn:aws:iam::123456789012:role/my-role\"}").roles(Role.administrator(tenantName)),
                "{\"message\":\"AWS archive access role set to 'arn:aws:iam::123456789012:role/my-role' for tenant scoober.\"}", 200);
        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertTrue(response.getBodyAsString().contains("\"awsRole\":\"arn:aws:iam::123456789012:role/my-role\"")),
                200);
        tester.assertResponse(request("/application/v4/tenant/scoober/archive-access/aws", DELETE).roles(Role.administrator(tenantName)),
                "{\"message\":\"AWS archive access role removed for tenant scoober.\"}", 200);
        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertFalse(response.getBodyAsString().contains("\"awsRole\":\"arn:aws:iam::123456789012:role/my-role\"")),
                200);

        tester.assertResponse(request("/application/v4/tenant/scoober/archive-access/gcp", PUT)
                        .data("{\"member\":\"user:test@example.com\"}").roles(Role.administrator(tenantName)),
                "{\"message\":\"GCP archive access member set to 'user:test@example.com' for tenant scoober.\"}", 200);
        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertTrue(response.getBodyAsString().contains("\"gcpMember\":\"user:test@example.com\"")),
                200);
        tester.assertResponse(request("/application/v4/tenant/scoober/archive-access/gcp", DELETE).roles(Role.administrator(tenantName)),
                "{\"message\":\"GCP archive access member removed for tenant scoober.\"}", 200);
        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertFalse(response.getBodyAsString().contains("\"gcpMember\":\"user:test@example.com\"")),
                200);

        tester.assertResponse(request("/application/v4/tenant/scoober/archive-access/aws", PUT)
                        .data("{\"role\":\"arn:aws:iam::123456789012:role/my-role\"}").roles(Role.administrator(tenantName)),
                "{\"message\":\"AWS archive access role set to 'arn:aws:iam::123456789012:role/my-role' for tenant scoober.\"}", 200);
        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertTrue(response.getBodyAsString().contains("\"awsRole\":\"arn:aws:iam::123456789012:role/my-role\"")),
                200);

        tester.assertResponse(request("/application/v4/tenant/scoober/archive-access/aws", PUT)
                        .data("{\"role\":\"arn:aws:iam::123456789012:role/my-role\"}").roles(Role.administrator(tenantName)),
                "{\"message\":\"AWS archive access role set to 'arn:aws:iam::123456789012:role/my-role' for tenant scoober.\"}", 200);
        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertTrue(response.getBodyAsString().contains("\"awsRole\":\"arn:aws:iam::123456789012:role/my-role\"")),
                200);

        tester.assertResponse(request("/application/v4/tenant/scoober/application/albums/environment/prod/region/aws-us-east-1c/instance/default", GET)
                        .roles(Role.reader(tenantName)),
                new File("deployment-cloud.json"));

        tester.assertResponse(request("/application/v4/tenant/scoober/archive-access/aws", DELETE).roles(Role.administrator(tenantName)),
                "{\"message\":\"AWS archive access role removed for tenant scoober.\"}", 200);
        tester.assertResponse(request("/application/v4/tenant/scoober", GET).roles(Role.reader(tenantName)),
                (response) -> assertFalse(response.getBodyAsString().contains("archiveAccessRole")),
                200);
    }

    @Test
    void create_application_on_deploy() {
        var application = ApplicationName.from("unique");
        var applicationPackage = new ApplicationPackageBuilder().trustDefaultCertificate().withoutAthenzIdentity().build();

        new ControllerTester(tester).upgradeSystem(new Version("6.1"));
        assertTrue(tester.controller().applications().getApplication(TenantAndApplicationId.from(tenantName, application)).isEmpty());

        tester.assertResponse(
                request("/application/v4/tenant/scoober/application/unique/instance/default/deploy/dev-aws-us-east-1c", POST)
                        .data(createApplicationDeployData(Optional.of(applicationPackage), Optional.empty(), true))
                        .roles(Set.of(Role.developer(tenantName))),
                "{\"message\":\"Deployment started in run 1 of dev-aws-us-east-1c for scoober.unique. This may take about 15 minutes the first time.\",\"run\":1}");

        assertTrue(tester.controller().applications().getApplication(TenantAndApplicationId.from(tenantName, application)).isPresent());
    }

    @Test
    void create_application_on_submit() {
        var application = ApplicationName.from("unique");
        var applicationPackage = new ApplicationPackageBuilder()
                .trustDefaultCertificate()
                .withoutAthenzIdentity()
                .build();

        assertTrue(tester.controller().applications().getApplication(TenantAndApplicationId.from(tenantName, application)).isEmpty());

        var data = ApplicationApiTest.createApplicationSubmissionData(applicationPackage, 123);

        tester.assertResponse(
                request("/application/v4/tenant/scoober/application/unique/submit", POST)
                        .data(data)
                        .roles(Set.of(Role.developer(tenantName))),
                "{\"message\":\"application build 1, source revision of repository 'repository1', branch 'master' with commit 'commit1', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        assertTrue(tester.controller().applications().getApplication(TenantAndApplicationId.from(tenantName, application)).isPresent());
    }

    private ApplicationPackageBuilder prodBuilder() {
        return new ApplicationPackageBuilder()
                .withoutAthenzIdentity()
                .instances("default")
                .region("aws-us-east-1c");
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

    private void deployApplication() {
        var applicationPackage = new ApplicationPackageBuilder()
                .trustDefaultCertificate()
                .instances("default")
                .globalServiceId("foo")
                .region("aws-us-east-1c")
                .build();
        new ControllerTester(tester).upgradeSystem(new Version("6.1"));
        tester.controller().jobController().deploy(ApplicationId.from("scoober", "albums", "default"),
                                                   JobType.prod("aws-us-east-1c"),
                                                   Optional.empty(),
                                                   applicationPackage);
    }


    private MultiPartStreamer createApplicationDeployData(Optional<ApplicationPackage> applicationPackage,
                                                          Optional<ApplicationVersion> applicationVersion, boolean deployDirectly) {
        MultiPartStreamer streamer = new MultiPartStreamer();
        streamer.addJson("deployOptions", deployOptions(deployDirectly, applicationVersion));
        applicationPackage.ifPresent(ap -> streamer.addBytes("applicationZip", ap.zippedContent()));
        return streamer;
    }

    private String deployOptions(boolean deployDirectly, Optional<ApplicationVersion> applicationVersion) {
        return "{\"vespaVersion\":null," +
                "\"ignoreValidationErrors\":false," +
                "\"deployDirectly\":" + deployDirectly +
                applicationVersion.map(version ->
                        "," +
                                "\"buildNumber\":" + version.buildNumber().getAsLong() + "," +
                                "\"sourceRevision\":{" +
                                "\"repository\":\"" + version.source().get().repository() + "\"," +
                                "\"branch\":\"" + version.source().get().branch() + "\"," +
                                "\"commit\":\"" + version.source().get().commit() + "\"" +
                                "}"
                ).orElse("") +
                "}";
    }

}
