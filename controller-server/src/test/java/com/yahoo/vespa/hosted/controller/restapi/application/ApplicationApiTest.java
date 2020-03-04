// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import ai.vespa.hosted.api.MultiPartStreamer;
import ai.vespa.hosted.api.Signatures;
import com.yahoo.application.container.handler.Request;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.EnvironmentResource;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostInfo;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringData;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MockTenantCost;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMeteringClient;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.RotationStatusUpdater;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.routing.GlobalRouting;
import com.yahoo.vespa.hosted.controller.security.AthenzCredentials;
import com.yahoo.vespa.hosted.controller.security.AthenzTenantSpec;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static com.yahoo.application.container.handler.Request.Method.DELETE;
import static com.yahoo.application.container.handler.Request.Method.GET;
import static com.yahoo.application.container.handler.Request.Method.PATCH;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.application.container.handler.Request.Method.PUT;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 * @author bjorncs
 * @author jonmv
 */
public class ApplicationApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";
    private static final String pemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                               "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                               "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                               "-----END PUBLIC KEY-----\n";
    private static final String quotedPemPublicKey = pemPublicKey.replaceAll("\\n", "\\\\n");

    private static final ApplicationPackage applicationPackageDefault = new ApplicationPackageBuilder()
            .instances("default")
            .environment(Environment.prod)
            .globalServiceId("foo")
            .region("us-central-1")
            .region("us-east-3")
            .region("us-west-1")
            .blockChange(false, true, "mon-fri", "0-8", "UTC")
            .build();

    private static final ApplicationPackage applicationPackageInstance1 = new ApplicationPackageBuilder()
            .instances("instance1")
            .environment(Environment.prod)
            .globalServiceId("foo")
            .region("us-central-1")
            .region("us-east-3")
            .region("us-west-1")
            .blockChange(false, true, "mon-fri", "0-8", "UTC")
            .build();

    private static final AthenzDomain ATHENZ_TENANT_DOMAIN = new AthenzDomain("domain1");
    private static final AthenzDomain ATHENZ_TENANT_DOMAIN_2 = new AthenzDomain("domain2");
    private static final ScrewdriverId SCREWDRIVER_ID = new ScrewdriverId("12345");
    private static final UserId USER_ID = new UserId("myuser");
    private static final UserId OTHER_USER_ID = new UserId("otheruser");
    private static final UserId HOSTED_VESPA_OPERATOR = new UserId("johnoperator");
    private static final OktaIdentityToken OKTA_IT = new OktaIdentityToken("okta-it");
    private static final OktaAccessToken OKTA_AT = new OktaAccessToken("okta-at");


    private ContainerTester tester;
    private DeploymentTester deploymentTester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        deploymentTester = new DeploymentTester(new ControllerTester(tester));
        deploymentTester.controllerTester().computeVersionStatus();
    }

    @Test
    public void testApplicationApi() {
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID); // (Necessary but not provided in this API)

        // GET API root
        tester.assertResponse(request("/application/v4/", GET).userIdentity(USER_ID),
                              new File("root.json"));
        // POST (add) a tenant without property ID
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"));
        // PUT (modify) a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              new File("tenant-without-applications.json"));
        // GET the authenticated user (with associated tenants)
        tester.assertResponse(request("/application/v4/user", GET).userIdentity(USER_ID),
                              new File("user.json"));
        // TODO jonmv: Remove when dashboard is gone.
        // PUT a user tenant — does nothing
        tester.assertResponse(request("/application/v4/user", PUT).userIdentity(USER_ID),
                              "");
        // GET the authenticated user which now exists (with associated tenants)
        tester.assertResponse(request("/application/v4/user", GET).userIdentity(USER_ID),
                              new File("user.json"));
        // DELETE the user — it doesn't exist, so access control fails
        tester.assertResponse(request("/application/v4/tenant/by-myuser", DELETE).userIdentity(USER_ID),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}", 403);
        // GET all tenants
        tester.assertResponse(request("/application/v4/tenant/", GET).userIdentity(USER_ID),
                              new File("tenant-list.json"));

        // GET list of months for a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1/cost", GET).userIdentity(USER_ID).oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                "{\"months\":[]}");

        // GET cost for a month for a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1/cost/2018-01", GET).userIdentity(USER_ID).oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                "{\"month\":\"2018-01\",\"items\":[]}");

        // Add another Athens domain, so we can try to create more tenants
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN_2, USER_ID); // New domain to test tenant w/property ID
        // Add property info for that property id, as well, in the mock organization.
        registerContact(1234);

        // POST (add) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}"),
                              new File("tenant-without-applications-with-id.json"));
        // PUT (modify) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", PUT)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}"),
                              new File("tenant-without-applications-with-id.json"));
        // GET a tenant with property ID and contact information
        updateContactInformation();
        tester.assertResponse(request("/application/v4/tenant/tenant2", GET).userIdentity(USER_ID),
                              new File("tenant-with-contact-info.json"));

        // POST (create) an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference.json"));
        // GET a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", GET).userIdentity(USER_ID),
                              new File("tenant-with-application.json"));

        // GET tenant applications
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/", GET).userIdentity(USER_ID),
                              new File("application-list.json"));
        // GET tenant applications (instances of "application1" only)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/", GET).userIdentity(USER_ID),
                              new File("application-list.json"));
        // GET at a tenant, with "&recursive=true&production=true", recurses over no instances yet, as they are not in deployment spec.
        tester.assertResponse(request("/application/v4/tenant/tenant1/", GET)
                                      .userIdentity(USER_ID)
                                      .properties(Map.of("recursive", "true",
                                                         "production", "true")),
                              new File("tenant-without-applications.json"));
        // GET at an application, with "&recursive=true&production=true", recurses over no instances yet, as they are not in deployment spec.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", GET)
                                      .userIdentity(USER_ID)
                                      .properties(Map.of("recursive", "true",
                                                         "production", "true")),
                              new File("application-without-instances.json"));

        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));

        ApplicationId id = ApplicationId.from("tenant1", "application1", "instance1");
        var app1 = deploymentTester.newDeploymentContext(id);

        // POST (deploy) an application to start a manual deployment in prod is not allowed
        MultiPartStreamer entity = createApplicationDeployData(applicationPackageInstance1, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploy/production-us-east-3/", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Direct deployments are only allowed to manually deployed environments.\"}", 400);

        // POST (deploy) an application to start a manual deployment in prod is allowed for operators
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploy/production-us-east-3/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"message\":\"Deployment started in run 1 of production-us-east-3 for tenant1.application1.instance1. This may take about 15 minutes the first time.\",\"run\":1}");
        app1.runJob(JobType.productionUsEast3);
        tester.controller().applications().deactivate(app1.instanceId(), ZoneId.from("prod", "us-east-3"));

        // POST (deploy) an application to start a manual deployment to dev
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploy/dev-us-east-1/", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Deployment started in run 1 of dev-us-east-1 for tenant1.application1.instance1. This may take about 15 minutes the first time.\",\"run\":1}");
        app1.runJob(JobType.devUsEast1);

        // GET dev application package
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/dev-us-east-1/package", GET)
                                      .userIdentity(USER_ID),
                              (response) -> {
                                  assertEquals("attachment; filename=\"tenant1.application1.instance1.dev.us-east-1.zip\"", response.getHeaders().getFirst("Content-Disposition"));
                                  assertArrayEquals(applicationPackageInstance1.zippedContent(), response.getBody());
                              },
                              200);

        // POST an application package is not generally allowed under user instance
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/otheruser/deploy/dev-us-east-1", POST)
                                      .userIdentity(OTHER_USER_ID)
                                      .data(createApplicationDeployData(applicationPackageInstance1, false)),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // DELETE a dev deployment is not generally allowed under user instance
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/otheruser/environment/dev/region/us-east-1", DELETE)
                                      .userIdentity(OTHER_USER_ID),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // When the user is a tenant admin, user instances are allowed.
        // POST an application package is not allowed under user instance for tenant admins
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/myuser/deploy/dev-us-east-1", POST)
                                      .userIdentity(USER_ID)
                                      .data(createApplicationDeployData(applicationPackageInstance1, false)),
                              new File("deployment-job-accepted-2.json"));

        // DELETE a dev deployment is allowed under user instance for tenant admins
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/myuser/environment/dev/region/us-east-1", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.myuser in dev.us-east-1\"}");

        // DELETE a user instance
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/myuser", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant1.application1.myuser\"}");

        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN,
                                       id.application());

        // POST an application package and a test jar, submitting a new application for production deployment.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(applicationPackageInstance1, 123)),
                              "{\"message\":\"Application package version: 1.0.1-commit1, source revision of repository 'repository1', branch 'master' with commit 'commit1', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        app1.runJob(JobType.systemTest).runJob(JobType.stagingTest).runJob(JobType.productionUsCentral1);

        // POST an application deployment to a production zone - operator emergency deployment - fails since package is unknown
        entity = createApplicationDeployData(Optional.empty(),
                                             Optional.of(ApplicationVersion.from(DeploymentContext.defaultSourceRevision, 666)),
                                             true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No application package found for tenant1.application1 with version 1.0.666-commit1\"}",
                              400);

        // POST an application deployment to a production zone - operator emergency deployment - works with known package
        entity = createApplicationDeployData(Optional.empty(),
                                             Optional.of(ApplicationVersion.from(DeploymentContext.defaultSourceRevision, 1)),
                                             true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              new File("deploy-result.json"));

        // POST an application deployment to a production zone - operator emergency deployment - chooses latest package without arguments
        entity = createApplicationDeployData(Optional.empty(), true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              new File("deploy-result.json"));

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1")
                .globalServiceId("foo")
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .allow(ValidationId.globalEndpointChange)
                .build();

        // POST (create) another application
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/instance/default", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference-2.json"));

        ApplicationId id2 = ApplicationId.from("tenant2", "application2", "instance1");
        var app2 = deploymentTester.newDeploymentContext(id2);
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN_2,
                                       id2.application());

        // Trigger upgrade and then application change
        deploymentTester.applications().deploymentTrigger().triggerChange(id2, Change.of(Version.fromString("7.0")));

        // POST an application package and a test jar, submitting a new application for production deployment.
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(applicationPackage, 1000)),
                              "{\"message\":\"Application package version: 1.0.1-commit1, source revision of repository 'repository1', branch 'master' with commit 'commit1', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        deploymentTester.triggerJobs();

        // POST a triggering to force a production job to start without successful tests
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/instance/instance1/job/production-us-west-1", POST)
                                      .data("{\"skipTests\":true}")
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Triggered production-us-west-1 for tenant2.application2.instance1\"}");

        // GET application having both change and outstanding change
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", GET)
                                      .userIdentity(USER_ID),
                              new File("application2.json"));

        // GET application having both change and outstanding change
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", GET)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("application2.json"));

        // PATCH in a major version override
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", PATCH)
                                      .userIdentity(USER_ID)
                                      .data("{\"majorVersion\":7}"),
                              "{\"message\":\"Set major version to 7\"}");

        // POST a pem deploy key
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/key", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"key\":\"" + pemPublicKey + "\"}"),
                              "{\"keys\":[\"-----BEGIN PUBLIC KEY-----\\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\\nz/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\\n-----END PUBLIC KEY-----\\n\"]}");

        // PATCH in a pem deploy key at deprecated path
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/instance/default", PATCH)
                                      .userIdentity(USER_ID)
                                      .data("{\"pemDeployKey\":\"" + pemPublicKey + "\"}"),
                              "{\"message\":\"Added deploy key " + quotedPemPublicKey + "\"}");

        // GET an application with a major version override
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", GET)
                                      .userIdentity(USER_ID),
                              new File("application2-with-patches.json"));

        // PATCH in removal of the application major version override removal
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", PATCH)
                                      .userIdentity(USER_ID)
                                      .data("{\"majorVersion\":null}"),
                              "{\"message\":\"Set major version to empty\"}");

        // DELETE the pem deploy key
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/key", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"key\":\"" + pemPublicKey + "\"}"),
                              "{\"keys\":[]}");

        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", GET)
                                      .userIdentity(USER_ID),
                              new File("application2.json"));

        // DELETE instance 1 of 2
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/instance/default", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant2.application2.default\"}");

        // DELETE application with only one instance left
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted application tenant2.application2\"}");

        // Set version 6.1 to broken to change compile version for.
        deploymentTester.upgrader().overrideConfidence(Version.fromString("6.1"), VespaVersion.Confidence.broken);
        deploymentTester.controllerTester().computeVersionStatus();
        setDeploymentMaintainedInfo();
        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "us-central-1"));

        // GET tenant application deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", GET)
                                      .userIdentity(USER_ID),
                              new File("instance.json"));
        // GET an application deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1", GET)
                                      .userIdentity(USER_ID),
                              new File("deployment.json"));

        addIssues(deploymentTester, TenantAndApplicationId.from("tenant1", "application1"));
        // GET at root, with "&recursive=deployment", returns info about all tenants, their applications and their deployments
        tester.assertResponse(request("/application/v4/", GET)
                                      .userIdentity(USER_ID)
                                      .recursive("deployment"),
                              new File("recursive-root.json"));
        // GET at root, with "&recursive=tenant", returns info about all tenants, with limited info about their applications.
        tester.assertResponse(request("/application/v4/", GET)
                                      .userIdentity(USER_ID)
                                      .recursive("tenant"),
                              new File("recursive-until-tenant-root.json"));
        // GET at a tenant, with "&recursive=true", returns full info about their applications and their deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/", GET)
                                      .userIdentity(USER_ID)
                                      .recursive("true"),
                              new File("tenant1-recursive.json"));
        // GET at an application, with "&recursive=true", returns full info about its deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", GET)
                                      .userIdentity(USER_ID)
                                      .recursive("true"),
                              new File("instance1-recursive.json"));

        // GET nodes
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/nodes", GET)
                             .userIdentity(USER_ID),
                              new File("application-nodes.json"));

        // GET logs
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application1/environment/dev/region/us-central-1/instance/default/logs?from=1233&to=3214", GET)
                        .userIdentity(USER_ID),
                "INFO - All good");

        // DELETE (cancel) ongoing change
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", DELETE)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"message\":\"Changed deployment from 'application change to 1.0.1-commit1' to 'no change' for tenant1.application1.instance1\"}");

        // DELETE (cancel) again is a no-op
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"cancel\":\"all\"}"),
                              "{\"message\":\"No deployment in progress for tenant1.application1.instance1 at this time\"}");

        // POST pinning to a given version to an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", POST)
                                      .userIdentity(USER_ID)
                                      .data("6.1.0"),
                              "{\"message\":\"Triggered pin to 6.1 for tenant1.application1.instance1\"}");
        assertTrue("Action is logged to audit log",
                   tester.controller().auditLogger().readLog().entries().stream()
                         .anyMatch(entry -> entry.resource().equals("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin?")));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");

        // DELETE only the pin to a given version
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to 6.1' to 'upgrade to 6.1' for tenant1.application1.instance1\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":false}");

        // POST pinning again
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", POST)
                                      .userIdentity(USER_ID)
                                      .data("6.1"),
                              "{\"message\":\"Triggered pin to 6.1 for tenant1.application1.instance1\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");

        // DELETE only the version, but leave the pin
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/platform", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to 6.1' to 'pin to current platform' for tenant1.application1.instance1\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"pinned\":true}");

        // DELETE also the pin to a given version
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to current platform' to 'no change' for tenant1.application1.instance1\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{}");

        // POST a pause to a production job
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/production-us-west-1/pause", POST)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"production-us-west-1 for tenant1.application1.instance1 paused for " + DeploymentTrigger.maxPause + "\"}");

        // DELETE a pause of a production job
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/production-us-west-1/pause", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"production-us-west-1 for tenant1.application1.instance1 resumed\"}");

        // POST a triggering to the same production job
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/production-us-west-1", POST)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Triggered production-us-west-1 for tenant1.application1.instance1\"}");

        // POST a 'restart application' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/restart", POST)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Requested restart of tenant1.application1.instance1 in prod.us-central-1\"}");

        // POST a 'restart application' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/restart", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Requested restart of tenant1.application1.instance1 in prod.us-central-1\"}");

        // POST a 'restart application' in staging environment command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-central-1/instance/instance1/restart", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Requested restart of tenant1.application1.instance1 in staging.us-central-1\"}");

        // POST a 'restart application' in staging test command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-central-1/instance/instance1/restart", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Requested restart of tenant1.application1.instance1 in test.us-central-1\"}");

        // POST a 'restart application' in staging dev command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-central-1/instance/instance1/restart", POST)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Requested restart of tenant1.application1.instance1 in dev.us-central-1\"}");

        // POST a 'restart application' command with a host filter (other filters not supported yet)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/restart", POST)
                                      .properties(Map.of("hostname", "node-1-tenant-host-prod.us-central-1"))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Requested restart of tenant1.application1.instance1 in prod.us-central-1\"}", 200);

        // GET suspended
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/suspended", GET)
                                      .userIdentity(USER_ID),
                              new File("suspended.json"));

        // GET services
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/service", GET)
                                      .userIdentity(USER_ID),
                              new File("services.json"));
        // GET service
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/service/storagenode-awe3slno6mmq2fye191y324jl/state/v1/", GET)
                                      .userIdentity(USER_ID),
                              new File("service.json"));

        // DELETE application with active deployments fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("delete-with-active-deployments.json"), 400);

        // DELETE (deactivate) a deployment - dev
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-east-1/instance/instance1", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.instance1 in dev.us-east-1\"}");

        // DELETE (deactivate) a deployment - prod
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.instance1 in prod.us-central-1\"}");


        // DELETE (deactivate) a deployment is idempotent
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.instance1 in prod.us-central-1\"}");

        // Setup for test config tests
        tester.controller().applications().deploy(ApplicationId.from("tenant1", "application1", "default"),
                                                  ZoneId.from("prod", "us-central-1"),
                                                  Optional.of(applicationPackageDefault),
                                                  new DeployOptions(true, Optional.empty(), false, false));
        tester.controller().applications().deploy(ApplicationId.from("tenant1", "application1", "my-user"),
                                                  ZoneId.from("dev", "us-east-1"),
                                                  Optional.of(applicationPackageDefault),
                                                  new DeployOptions(false, Optional.empty(), false, false));

        // GET test-config for local tests against a dev deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/my-user/job/dev-us-east-1/test-config", GET)
                                      .userIdentity(USER_ID),
                              new File("test-config-dev.json"));
        // GET test-config for local tests against a prod deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/my-user/job/production-us-central-1/test-config", GET)
                                      .userIdentity(USER_ID),
                              new File("test-config.json"));
        tester.controller().applications().deactivate(ApplicationId.from("tenant1", "application1", "default"),
                                                      ZoneId.from("prod", "us-central-1"));
        tester.controller().applications().deactivate(ApplicationId.from("tenant1", "application1", "my-user"),
                                                      ZoneId.from("dev", "us-east-1"));
        // teardown for test config tests

        // Second attempt has a service under a different domain than the tenant of the application, and fails.
        ApplicationPackage packageWithServiceForWrongDomain = new ApplicationPackageBuilder()
                .instances("instance1")
                .environment(Environment.prod)
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from(ATHENZ_TENANT_DOMAIN_2.getName()), AthenzService.from("service"))
                .region("us-west-1")
                .build();
        allowLaunchOfService(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN_2, "service"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(packageWithServiceForWrongDomain, 123)),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz domain in deployment.xml: [domain2] must match tenant domain: [domain1]\"}", 400);

        // Third attempt has a service under the domain of the tenant, and also succeeds.
        ApplicationPackage packageWithService = new ApplicationPackageBuilder()
                .instances("instance1")
                .globalServiceId("foo")
                .environment(Environment.prod)
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from(ATHENZ_TENANT_DOMAIN.getName()), AthenzService.from("service"))
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .build();
        allowLaunchOfService(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(packageWithService, 123)),
                              "{\"message\":\"Application package version: 1.0.2-commit1, source revision of repository 'repository1', branch 'master' with commit 'commit1', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        // GET last submitted application package
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              (response) -> {
                                  assertEquals("attachment; filename=\"tenant1.application1-build2.zip\"", response.getHeaders().getFirst("Content-Disposition"));
                                  assertArrayEquals(packageWithService.zippedContent(), response.getBody());
                              },
                              200);

        // GET application package for previous build
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET)
                                      .properties(Map.of("build", "1"))
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              (response) -> {
                                  assertEquals("attachment; filename=\"tenant1.application1-build1.zip\"", response.getHeaders().getFirst("Content-Disposition"));
                                  assertArrayEquals(applicationPackageInstance1.zippedContent(), response.getBody());
                              },
                              200);

        // Fourth attempt has a wrong content hash in a header, and fails.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .header("X-Content-Hash", "not/the/right/hash")
                                      .data(createApplicationSubmissionData(packageWithService, 123)),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Value of X-Content-Hash header does not match computed content hash\"}", 400);

        // Fifth attempt has the right content hash in a header, and succeeds.
        MultiPartStreamer streamer = createApplicationSubmissionData(packageWithService, 123);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .header("X-Content-Hash", Base64.getEncoder().encodeToString(Signatures.sha256Digest(streamer::data)))
                                      .data(streamer),
                              "{\"message\":\"Application package version: 1.0.3-commit1, source revision of repository 'repository1', branch 'master' with commit 'commit1', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        // Sixth attempt has a multi-instance deployment spec, and is accepted.
        ApplicationPackage multiInstanceSpec = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .environment(Environment.prod)
                .region("us-central-1")
                .parallel("us-west-1", "us-east-3")
                .endpoint("default", "foo", "us-central-1", "us-west-1", "us-east-3")
                .build();
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(multiInstanceSpec, 123)),
                              "{\"message\":\"Application package version: 1.0.4-commit1, source revision of repository 'repository1', branch 'master' with commit 'commit1', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");


        // GET deployment job overview, after triggering system and staging test jobs.
        assertEquals(2, tester.controller().applications().deploymentTrigger().triggerReadyJobs());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job", GET)
                                      .userIdentity(USER_ID),
                              new File("jobs.json"));

        // GET deployment job overview for whole application.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deployment", GET)
                                      .userIdentity(USER_ID),
                              new File("deployment-overview.json"));

        // GET system test job overview.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/system-test", GET)
                                      .userIdentity(USER_ID),
                              new File("system-test-job.json"));

        // GET system test run 1 details.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/system-test/run/1", GET)
                                      .userIdentity(USER_ID),
                              new File("system-test-details.json"));

        // DELETE a running job to have it aborted.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/staging-test", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Aborting run 2 of staging-test for tenant1.application1.instance1\"}");

        // GET user lists only tenants for the authenticated user
        tester.assertResponse(request("/application/v4/user", GET)
                                      .userIdentity(new UserId("other_user")),
                              "{\"user\":\"other_user\",\"tenants\":[],\"tenantExists\":true}");

        // OPTIONS return 200 OK
        tester.assertResponse(request("/application/v4/", Request.Method.OPTIONS)
                                      .userIdentity(USER_ID),
                              "");

        // DELETE the application which no longer has any deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted application tenant1.application1\"}");

        // DELETE an empty tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE).userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"));
    }

    private void addIssues(DeploymentTester tester, TenantAndApplicationId id) {
        tester.applications().lockApplicationOrThrow(id, application ->
                tester.controller().applications().store(application.withDeploymentIssueId(IssueId.from("123"))
                                                                    .withOwnershipIssueId(IssueId.from("321"))
                                                                    .withOwner(User.from("owner-username"))));
    }

    @Test
    public void testRotationOverride() {
        // Setup
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        var westZone = ZoneId.from("prod", "us-west-1");
        var eastZone = ZoneId.from("prod", "us-east-3");
        var applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1")
                .globalServiceId("foo")
                .region(westZone.region())
                .region(eastZone.region())
                .build();

        // Create tenant and deploy
        var app = deploymentTester.newDeploymentContext(createTenantAndApplication());
        app.submit(applicationPackage).deploy();

        // Invalid application fails
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"tenant2.application2 not found\"}",
                              400);

        // Invalid deployment fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-central-1/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"application 'tenant1.application1.instance1' has no deployment in prod.us-central-1\"}",
                              404);

        // Change status of non-existing deployment fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-central-1/global-rotation/override", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"application 'tenant1.application1.instance1' has no deployment in prod.us-central-1\"}",
                              404);

        // GET global rotation status
        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "us-west-1"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              new File("global-rotation.json"));

        // GET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation/override", GET)
                                      .userIdentity(USER_ID),
                              new File("global-rotation-get.json"));

        // SET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation/override", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              new File("global-rotation-put.json"));

        // Status of routing policy is changed
        assertGlobalRouting(app.deploymentIdIn(westZone), GlobalRouting.Status.out, GlobalRouting.Agent.tenant);

        // DELETE global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation/override", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              new File("global-rotation-delete.json"));
        assertGlobalRouting(app.deploymentIdIn(westZone), GlobalRouting.Status.in, GlobalRouting.Agent.tenant);

        // SET global rotation override status by operator
        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation/override", PUT)
                                      .userIdentity(HOSTED_VESPA_OPERATOR)
                                      .data("{\"reason\":\"unit-test\"}"),
                              new File("global-rotation-put.json"));
        assertGlobalRouting(app.deploymentIdIn(westZone), GlobalRouting.Status.out, GlobalRouting.Agent.operator);
    }

    @Test
    public void multiple_endpoints() {
        // Setup
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1")
                .region("us-west-1")
                .region("us-east-3")
                .region("eu-west-1")
                .endpoint("eu", "default", "eu-west-1")
                .endpoint("default", "default", "us-west-1", "us-east-3")
                .build();

        // Create tenant and deploy
        var app = deploymentTester.newDeploymentContext("tenant1", "application1", "instance1");
        app.submit(applicationPackage).deploy();

        setZoneInRotation("rotation-fqdn-2", ZoneId.from("prod", "us-west-1"));
        setZoneInRotation("rotation-fqdn-2", ZoneId.from("prod", "us-east-3"));
        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "eu-west-1"));

        // GET global rotation status without specifying endpointId fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"application 'tenant1.application1.instance1' has multiple rotations. Query parameter 'endpointId' must be given\"}",
                              400);

        // GET global rotation status for us-west-1 in default endpoint
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation", GET)
                                      .properties(Map.of("endpointId", "default"))
                                      .userIdentity(USER_ID),
                              "{\"bcpStatus\":{\"rotationStatus\":\"IN\"}}",
                              200);

        // GET global rotation status for us-west-1 in eu endpoint
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation", GET)
                                      .properties(Map.of("endpointId", "eu"))
                                      .userIdentity(USER_ID),
                              "{\"bcpStatus\":{\"rotationStatus\":\"UNKNOWN\"}}",
                              200);

        // GET global rotation status for eu-west-1 in eu endpoint
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/eu-west-1/global-rotation", GET)
                                      .properties(Map.of("endpointId", "eu"))
                                      .userIdentity(USER_ID),
                              "{\"bcpStatus\":{\"rotationStatus\":\"IN\"}}",
                              200);
    }

    @Test
    public void testDeployDirectly() {
        // Setup
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));

        // Create tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST).userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"));

        // Create application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference.json"));

        // Grant deploy access
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN,
                                       ApplicationName.from("application1"));

        // POST (deploy) an application to a prod zone - allowed when project ID is not specified
        MultiPartStreamer entity = createApplicationDeployData(applicationPackageInstance1, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));

        // POST (deploy) a system application with an application package
        MultiPartStreamer noAppEntity = createApplicationDeployData(Optional.empty(), true);
        tester.assertResponse(request("/application/v4/tenant/hosted-vespa/application/routing/environment/prod/region/us-central-1/instance/default/deploy", POST)
                                      .data(noAppEntity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Deployment of system applications during a system upgrade is not allowed\"}",
                              400);
        deploymentTester.controllerTester().upgradeSystem(deploymentTester.controller().versionStatus().controllerVersion().get().versionNumber());
        tester.assertResponse(request("/application/v4/tenant/hosted-vespa/application/routing/environment/prod/region/us-central-1/instance/default/deploy", POST)
                        .data(noAppEntity)
                        .userIdentity(HOSTED_VESPA_OPERATOR),
                new File("deploy-result.json"));

        // POST (deploy) a system application without an application package
        tester.assertResponse(request("/application/v4/tenant/hosted-vespa/application/proxy-host/environment/prod/region/us-central-1/instance/instance1/deploy", POST)
                        .data(noAppEntity)
                        .userIdentity(HOSTED_VESPA_OPERATOR),
                new File("deploy-no-deployment.json"), 400);
    }

    @Test
    public void testMeteringResponses() {
        MockMeteringClient mockMeteringClient = tester.serviceRegistry().meteringService();

        // Mock response for MeteringClient
        ResourceAllocation currentSnapshot = new ResourceAllocation(1, 2, 3);
        ResourceAllocation thisMonth = new ResourceAllocation(12, 24, 1000);
        ResourceAllocation lastMonth = new ResourceAllocation(24, 48, 2000);
        ApplicationId applicationId = ApplicationId.from("doesnotexist", "doesnotexist", "default");
        Map<ApplicationId, List<ResourceSnapshot>> snapshotHistory = Map.of(applicationId, List.of(
                new ResourceSnapshot(applicationId, 1, 2,3, Instant.ofEpochMilli(123), ZoneId.defaultId()),
                new ResourceSnapshot(applicationId, 1, 2,3, Instant.ofEpochMilli(246), ZoneId.defaultId()),
                new ResourceSnapshot(applicationId, 1, 2,3, Instant.ofEpochMilli(492), ZoneId.defaultId())));

        mockMeteringClient.setMeteringData(new MeteringData(thisMonth, lastMonth, currentSnapshot, snapshotHistory));

        tester.assertResponse(request("/application/v4/tenant/doesnotexist/application/doesnotexist/metering", GET)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance1-metering.json"));
    }

    @Test
    public void testTenantCostResponse() {
        ApplicationId applicationId = createTenantAndApplication();
        MockTenantCost mockTenantCost = deploymentTester.controllerTester().serviceRegistry().tenantCost();

        mockTenantCost.setMonthsWithMetering(
                new TreeSet<>(Set.of(
                        YearMonth.of(2019, 10),
                        YearMonth.of(2019, 9)
                ))
        );

        tester.assertResponse(request("/application/v4/tenant/" + applicationId.tenant().value() + "/cost", GET)
                        .userIdentity(USER_ID)
                        .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                "{\"months\":[\"2019-09\",\"2019-10\"]}");

        CostInfo costInfo1 = new CostInfo(applicationId, ZoneId.from("prod", "us-south-1"),
                new BigDecimal("7.0"),
                new BigDecimal("600.0"),
                new BigDecimal("1000.0"),
                35, 23, 10);
        CostInfo costInfo2 = new CostInfo(applicationId, ZoneId.from("prod", "us-north-1"),
                new BigDecimal("2.0"),
                new BigDecimal("3.0"),
                new BigDecimal("4.0"),
                10, 20, 30);

        mockTenantCost.setCostInfoList(
                List.of(costInfo1, costInfo2)
        );

        tester.assertResponse(request("/application/v4/tenant/" + applicationId.tenant().value() + "/cost/2019-09", GET)
                        .userIdentity(USER_ID)
                        .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                new File("cost-report.json"));
    }

    @Test
    public void testErrorResponses() throws Exception {
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        // PUT (update) non-existing tenant returns 403 as tenant access cannot be determined when the tenant does not exist
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // GET non-existing tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'tenant1' does not exist\"}",
                              404);

        // GET non-existing application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"tenant1.application1 not found\"}",
                              404);

        // GET non-existing deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east/instance/default", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"tenant1.application1 not found\"}",
                              404);

        // POST (add) a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"));

        // POST (add) another tenant under the same domain
        tester.assertResponse(request("/application/v4/tenant/tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not create tenant 'tenant2': The Athens domain 'domain1' is already connected to tenant 'tenant1'\"}",
                              400);

        // Add the same tenant again
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Tenant 'tenant1' already exists\"}",
                              400);

        // POST (add) an Athenz tenant with underscore in name
        tester.assertResponse(request("/application/v4/tenant/my_tenant_2", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"New tenant or application names must start with a letter, may contain no more than 20 characters, and may only contain lowercase letters, digits or dashes, but no double-dashes.\"}",
                              400);

        // POST (add) an Athenz tenant with by- prefix
        tester.assertResponse(request("/application/v4/tenant/by-tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz tenant name cannot have prefix 'by-'\"}",
                              400);

        // POST (add) an Athenz tenant with a reserved name
        tester.assertResponse(request("/application/v4/tenant/hosted-vespa", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Tenant 'hosted-vespa' already exists\"}",
                              400);

        // POST (create) an (empty) application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference.json"));

        // Create the same application again
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", POST)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not create 'tenant1.application1.instance1': Instance already exists\"}",
                              400);

        ConfigServerMock configServer = tester.serviceRegistry().configServerMock();
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", "Invalid application package", ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE, null));

        // GET non-existent application package
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"No application package has been submitted for 'tenant1.application1'\"}",
                              404);

        // GET non-existent application package of specific build
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET)
                                      .properties(Map.of("build", "42"))
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"No application package found for 'tenant1.application1' with build number 42\"}",
                              404);

        // GET non-existent application package of invalid build
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET)
                                      .properties(Map.of("build", "foobar"))
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid build number: For input string: \\\"foobar\\\"\"}",
                              400);
        
        // POST (deploy) an application with an invalid application package
        MultiPartStreamer entity = createApplicationDeployData(applicationPackageInstance1, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-failure.json"), 400);

        // POST (deploy) an application without available capacity
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", "Out of capacity", ConfigServerException.ErrorCode.OUT_OF_CAPACITY, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-out-of-capacity.json"), 400);

        // POST (deploy) an application where activation fails
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to activate application", "Activation conflict", ConfigServerException.ErrorCode.ACTIVATION_CONFLICT, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-activation-conflict.json"), 409);

        // POST (deploy) an application where we get an internal server error
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to deploy application", "Internal server error", ConfigServerException.ErrorCode.INTERNAL_SERVER_ERROR, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-internal-server-error.json"), 500);

        // DELETE tenant which has an application
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not delete tenant 'tenant1': This tenant has active applications\"}",
                              400);

        // DELETE application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant1.application1.instance1\"}");
        // DELETE application again - should produce 404
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", DELETE)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not delete instance 'tenant1.application1.instance1': Instance not found\"}",
                              404);

        // GET cost of unknown tenant
        tester.assertResponse(request("/application/v4/tenant/no-such-tenant/cost", GET).userIdentity(USER_ID).oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'no-such-tenant' does not exist\"}", 404);

        tester.assertResponse(request("/application/v4/tenant/no-such-tenant/cost/2018-01-01", GET).userIdentity(USER_ID).oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'no-such-tenant' does not exist\"}", 404);

        // GET cost with invalid date string
        tester.assertResponse(request("/application/v4/tenant/tenant1/cost/not-a-valid-date", GET).userIdentity(USER_ID).oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not parse year-month 'not-a-valid-date'\"}", 400);

        // DELETE tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"));
        // DELETE tenant again returns 403 as tenant access cannot be determined when the tenant does not exist
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // Create legancy tenant name containing underscores
        tester.controller().curator().writeTenant(new AthenzTenant(TenantName.from("my_tenant"), ATHENZ_TENANT_DOMAIN,
                                                                   new Property("property1"), Optional.empty(), Optional.empty()));
        // POST (add) a Athenz tenant with dashes duplicates existing one with underscores
        tester.assertResponse(request("/application/v4/tenant/my-tenant", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Tenant 'my-tenant' already exists\"}",
                              400);
    }
    
    @Test
    public void testAuthorization() {
        UserId authorizedUser = USER_ID;
        UserId unauthorizedUser = new UserId("othertenant");
        
        // Mutation without an user is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              "{\n  \"message\" : \"Not authenticated\"\n}",
                              401);

        // ... but read methods are allowed for authenticated user
        tester.assertResponse(request("/application/v4/tenant/", GET)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              "[]",
                              200);

        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        // Creating a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT)
                                      .userIdentity(unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"The user 'user.othertenant' is not admin in Athenz domain 'domain1'\"}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"),
                              200);

        // Creating an application for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", POST)
                                      .userIdentity(unauthorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", POST)
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference.json"),
                              200);

        // Deploy to an authorized zone by a user tenant is disallowed
        MultiPartStreamer entity = createApplicationDeployData(applicationPackageDefault, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // Deleting an application for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(unauthorizedUser),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // Create another instance under the application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default", POST)
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference-default.json"),
                              200);

        // (Deleting the application with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted application tenant1.application1\"}",
                              200);

        // Updating a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .userIdentity(unauthorizedUser),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);
        
        // Change Athens domain
        createAthenzDomainWithAdmin(new AthenzDomain("domain2"), USER_ID);
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property1\"}")
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"tenant\":\"tenant1\",\"type\":\"ATHENS\",\"athensDomain\":\"domain2\",\"property\":\"property1\",\"applications\":[]}",
                              200);

        // Deleting a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(unauthorizedUser),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);
    }

    @Test
    public void athenz_service_must_be_allowed_to_launch_and_be_under_tenant_domain() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("another.domain"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        deploymentTester.controllerTester().createTenant("tenant1", ATHENZ_TENANT_DOMAIN.getName(), 1234L);
        var application = deploymentTester.newDeploymentContext("tenant1", "application1", "default");
        ScrewdriverId screwdriverId = new ScrewdriverId("123");
        addScrewdriverUserToDeployRole(screwdriverId, ATHENZ_TENANT_DOMAIN, application.instanceId().application());

        allowLaunchOfService(new com.yahoo.vespa.athenz.api.AthenzService(new AthenzDomain("another.domain"), "service"));
        // Submit a package with a service under a different Athenz domain from that of the tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit/", POST)
                                      .data(createApplicationSubmissionData(applicationPackage, 123))
                                      .screwdriverIdentity(screwdriverId),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz domain in deployment.xml: [another.domain] must match tenant domain: [domain1]\"}",
                              400);

        // Set the correct domain in the application package, but do not yet allow Vespa to launch the service.
        applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit", POST)
                                      .data(createApplicationSubmissionData(applicationPackage, 123))
                                      .screwdriverIdentity(screwdriverId),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Not allowed to launch Athenz service domain1.service\"}",
                              400);

        // Allow Vespa to launch the Athenz service.
        allowLaunchOfService(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"));

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit/", POST)
                                      .data(createApplicationSubmissionData(applicationPackage, 123))
                                      .screwdriverIdentity(screwdriverId),
                              "{\"message\":\"Application package version: 1.0.1-commit1, source revision of repository 'repository1', branch 'master' with commit 'commit1', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");
    }

    @Test
    public void personal_deployment_with_athenz_service_requires_user_is_admin() {
        // Setup
        UserId tenantAdmin = new UserId("tenant-admin");
        UserId userId = new UserId("new-user");
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, tenantAdmin);
        allowLaunchOfService(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"));

        // Create tenant
        // PUT (create) the authenticated user
        tester.assertResponse(request("/application/v4/user?user=new_user&domain=by", PUT)
                                      .userIdentity(userId), // Normalized to by-new-user by API
                              "");

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .build();

        // POST (deploy) an application to a dev zone fails because user tenant is used — these do not exist.
        MultiPartStreamer entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/by-new-user/application/application1/environment/dev/region/us-west-1/instance/default", POST)
                                      .data(entity)
                                      .userIdentity(userId),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        createTenantAndApplication();
        // POST (deploy) an application to dev through a deployment job, with user instance and a proper tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/new-user/deploy/dev-us-east-1", POST)
                                      .data(entity)
                                      .userIdentity(userId),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // Add "new-user" to the admin role, to allow service launches.
        tester.athenzClientFactory().getSetup()
                .domains.get(ATHENZ_TENANT_DOMAIN)
                        .admin(HostedAthenzIdentities.from(userId));

        // POST (deploy) an application to a dev zone fails because user tenant is used — these do not exist.
        tester.assertResponse(request("/application/v4/tenant/by-new-user/application/application1/environment/dev/region/us-west-1/instance/default", POST)
                                      .data(entity)
                                      .userIdentity(userId),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // POST (deploy) an application to dev through a deployment job
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/new-user/deploy/dev-us-east-1", POST)
                                      .data(entity)
                                      .userIdentity(userId),
                              "{\"message\":\"Deployment started in run 1 of dev-us-east-1 for tenant1.application1.new-user. This may take about 15 minutes the first time.\",\"run\":1}");

    }

    @Test
    // Deploy to sandbox tenant launching a service from another domain.
    public void developers_can_deploy_when_privileged() {
        // Create an athenz domain where the developer is not yet authorized
        UserId tenantAdmin = new UserId("tenant-admin");
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, tenantAdmin);
        allowLaunchOfService(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"));

        // Create the sandbox tenant and authorize the developer
        UserId developer = new UserId("developer");
        AthenzDomain sandboxDomain = new AthenzDomain("sandbox");
        createAthenzDomainWithAdmin(sandboxDomain, developer);
        AthenzTenantSpec tenantSpec = new AthenzTenantSpec(TenantName.from("sandbox"),
                sandboxDomain,
                new Property("vespa"),
                Optional.empty());
        AthenzCredentials credentials = new AthenzCredentials(
                new AthenzPrincipal(new AthenzUser(developer.id())), sandboxDomain, OKTA_IT, OKTA_AT);
        tester.controller().tenants().create(tenantSpec, credentials);
        tester.controller().applications().createApplication(TenantAndApplicationId.from("sandbox", "myapp"), credentials);

        // Create an application package referencing the service from the other domain
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .build();

        // deploy the application to a dev zone. Should fail since the developer is not authorized to launch the service
        MultiPartStreamer entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/sandbox/application/myapp/instance/default/deploy/dev-us-east-1", POST)
                        .data(entity)
                        .userIdentity(developer),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"User user.developer is not allowed to launch service domain1.service. Please reach out to the domain admin.\"}",
                400);

        // Allow developer launch privilege to domain1.service. Deployment now completes.
        AthenzDbMock.Domain domainMock = tester.athenzClientFactory().getSetup().getOrCreateDomain(ATHENZ_TENANT_DOMAIN);
        domainMock.withPolicy("user." + developer.id(), "launch", "service.service");


        tester.assertResponse(request("/application/v4/tenant/sandbox/application/myapp/instance/default/deploy/dev-us-east-1", POST)
                        .data(entity)
                        .userIdentity(developer),
                "{\"message\":\"Deployment started in run 1 of dev-us-east-1 for sandbox.myapp. This may take about 15 minutes the first time.\",\"run\":1}",
                200);

        // To add temporary support allowing tenant admins to launch services
        UserId developer2 = new UserId("developer2");
        // to be able to deploy to sandbox tenant
        tester.athenzClientFactory().getSetup().getOrCreateDomain(sandboxDomain).tenantAdmin(new AthenzUser(developer2.id()));
        tester.athenzClientFactory().getSetup().getOrCreateDomain(ATHENZ_TENANT_DOMAIN).tenantAdmin(new AthenzUser(developer2.id()));
        tester.assertResponse(request("/application/v4/tenant/sandbox/application/myapp/instance/default/deploy/dev-us-east-1", POST)
                        .data(entity)
                        .userIdentity(developer2),
                "{\"message\":\"Deployment started in run 2 of dev-us-east-1 for sandbox.myapp. This may take about 15 minutes the first time.\",\"run\":2}",
                200);

    }

    @Test
    public void applicationWithRoutingPolicy() {
        var app = deploymentTester.newDeploymentContext(createTenantAndApplication());
        var zone = ZoneId.from(Environment.prod, RegionName.from("us-west-1"));
        deploymentTester.controllerTester().zoneRegistry().setRoutingMethod(ZoneApiMock.from(zone),
                                                                            List.of(RoutingMethod.exclusive, RoutingMethod.shared));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .instances("instance1")
                .region(zone.region().value())
                .build();
        app.submit(applicationPackage).deploy();
        app.addInactiveRoutingPolicy(zone);

        // GET application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", GET)
                                      .userIdentity(USER_ID),
                              new File("instance-with-routing-policy.json"));

        // GET deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/instance1", GET)
                                      .userIdentity(USER_ID),
                              new File("deployment-with-routing-policy.json"));
    }

    private MultiPartStreamer createApplicationDeployData(ApplicationPackage applicationPackage, boolean deployDirectly) {
        return createApplicationDeployData(Optional.of(applicationPackage), deployDirectly);
    }

    private MultiPartStreamer createApplicationDeployData(Optional<ApplicationPackage> applicationPackage, boolean deployDirectly) {
        return createApplicationDeployData(applicationPackage, Optional.empty(), deployDirectly);
    }

    private MultiPartStreamer createApplicationDeployData(Optional<ApplicationPackage> applicationPackage,
                                                          Optional<ApplicationVersion> applicationVersion, boolean deployDirectly) {
        MultiPartStreamer streamer = new MultiPartStreamer();
        streamer.addJson("deployOptions", deployOptions(deployDirectly, applicationVersion));
        applicationPackage.ifPresent(ap -> streamer.addBytes("applicationZip", ap.zippedContent()));
        return streamer;
    }

    static MultiPartStreamer createApplicationSubmissionData(ApplicationPackage applicationPackage, long projectId) {
        return new MultiPartStreamer().addJson(EnvironmentResource.SUBMIT_OPTIONS, "{\"repository\":\"repository1\",\"branch\":\"master\",\"commit\":\"commit1\","
                                                                                   + "\"projectId\":" + projectId + ",\"authorEmail\":\"a@b\"}")
                                      .addBytes(EnvironmentResource.APPLICATION_ZIP, applicationPackage.zippedContent())
                                      .addBytes(EnvironmentResource.APPLICATION_TEST_ZIP, "content".getBytes());
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

    /** Make a request with (athens) user domain1.mytenant */
    private RequestBuilder request(String path, Request.Method method) {
        return new RequestBuilder(path, method);
    }

    /**
     * In production this happens outside hosted Vespa, so there is no API for it and we need to reach down into the
     * mock setup to replicate the action.
     */
    private void createAthenzDomainWithAdmin(AthenzDomain domain, UserId userId) {
        AthenzDbMock.Domain domainMock = tester.athenzClientFactory().getSetup().getOrCreateDomain(domain);
        domainMock.markAsVespaTenant();
        domainMock.admin(AthenzUser.fromUserId(userId.id()));
    }

    /**
     * Mock athenz service identity configuration. Simulates that configserver is allowed to launch a service
     */
    private void allowLaunchOfService(com.yahoo.vespa.athenz.api.AthenzService service) {
        AthenzDbMock.Domain domainMock = tester.athenzClientFactory().getSetup().getOrCreateDomain(service.getDomain());
        domainMock.withPolicy(tester.controller().zoneRegistry().accessControlDomain().value()+".provider.*","launch", "service." + service.getName());
    }

    /**
     * In production this happens outside hosted Vespa, so there is no API for it and we need to reach down into the
     * mock setup to replicate the action.
     */
    private void addScrewdriverUserToDeployRole(ScrewdriverId screwdriverId,
                                                AthenzDomain domain,
                                                ApplicationName application) {
        tester.authorize(domain, HostedAthenzIdentities.from(screwdriverId), ApplicationAction.deploy, application);
    }

    private ApplicationId createTenantAndApplication() {
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference.json"));
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID, ATHENZ_TENANT_DOMAIN, ApplicationName.from("application1"));

        return ApplicationId.from("tenant1", "application1", "instance1");
    }

    /**
     * Cluster info, utilization and application and deployment metrics are maintained async by maintainers.
     *
     * This sets these values as if the maintainers has been ran.
     */
    private void setDeploymentMaintainedInfo() {
        for (Application application : deploymentTester.applications().asList()) {
            deploymentTester.applications().lockApplicationOrThrow(application.id(), lockedApplication -> {
                lockedApplication = lockedApplication.with(new ApplicationMetrics(0.5, 0.7));

                for (Instance instance : application.instances().values()) {
                    for (Deployment deployment : instance.deployments().values()) {
                        DeploymentMetrics metrics = new DeploymentMetrics(1, 2, 3, 4, 5,
                                                                          Optional.of(Instant.ofEpochMilli(123123)), Map.of());
                        lockedApplication = lockedApplication.with(instance.name(),
                                                                   lockedInstance -> lockedInstance.with(deployment.zone(), metrics)
                                                                                                   .recordActivityAt(Instant.parse("2018-06-01T10:15:30.00Z"), deployment.zone()));
                    }
                    deploymentTester.applications().store(lockedApplication);
                }
            });
        }
    }

    private void setZoneInRotation(String rotationName, ZoneId zone) {
        tester.serviceRegistry().globalRoutingServiceMock().setStatus(rotationName, zone, com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus.IN);
        new RotationStatusUpdater(tester.controller(), Duration.ofDays(1), new JobControl(tester.controller().curator())).run();
    }

    private void updateContactInformation() {
        Contact contact = new Contact(URI.create("www.contacts.tld/1234"),
                                      URI.create("www.properties.tld/1234"),
                                      URI.create("www.issues.tld/1234"),
                                      List.of(List.of("alice"), List.of("bob")), "queue", Optional.empty());
        tester.controller().tenants().lockIfPresent(TenantName.from("tenant2"),
                                                    LockedTenant.Athenz.class,
                                                    lockedTenant -> tester.controller().tenants().store(lockedTenant.with(contact)));
    }

    private void registerContact(long propertyId) {
        PropertyId p = new PropertyId(String.valueOf(propertyId));
        tester.serviceRegistry().contactRetrieverMock().addContact(p, new Contact(URI.create("www.issues.tld/" + p.id()),
                                                                                  URI.create("www.contacts.tld/" + p.id()),
                                                                                  URI.create("www.properties.tld/" + p.id()),
                                                                                  List.of(Collections.singletonList("alice"),
                                                                                   Collections.singletonList("bob")),
                                                                                  "queue", Optional.empty()));
    }

    private void assertGlobalRouting(DeploymentId deployment, GlobalRouting.Status status, GlobalRouting.Agent agent) {
        var changedAt = tester.controller().clock().instant();
        var westPolicies = tester.controller().routing().policies().get(deployment);
        assertEquals(1, westPolicies.size());
        var westPolicy = westPolicies.values().iterator().next();
        assertEquals(status, westPolicy.status().globalRouting().status());
        assertEquals(agent, westPolicy.status().globalRouting().agent());
        assertEquals(changedAt.truncatedTo(ChronoUnit.MILLIS), westPolicy.status().globalRouting().changedAt());
    }

    private static class RequestBuilder implements Supplier<Request> {

        private final String path;
        private final Request.Method method;
        private byte[] data = new byte[0];
        private AthenzIdentity identity;
        private OktaIdentityToken oktaIdentityToken;
        private OktaAccessToken oktaAccessToken;
        private String contentType = "application/json";
        private final Map<String, List<String>> headers = new HashMap<>();
        private final Map<String, String> properties = new HashMap<>();

        private RequestBuilder(String path, Request.Method method) {
            this.path = path;
            this.method = method;
        }

        private RequestBuilder data(byte[] data) { this.data = data; return this; }
        private RequestBuilder data(String data) { return data(data.getBytes(UTF_8)); }
        private RequestBuilder data(MultiPartStreamer streamer) {
            return Exceptions.uncheck(() -> data(streamer.data().readAllBytes()).contentType(streamer.contentType()));
        }

        private RequestBuilder userIdentity(UserId userId) { this.identity = HostedAthenzIdentities.from(userId); return this; }
        private RequestBuilder screwdriverIdentity(ScrewdriverId screwdriverId) { this.identity = HostedAthenzIdentities.from(screwdriverId); return this; }
        private RequestBuilder oktaIdentityToken(OktaIdentityToken oktaIdentityToken) { this.oktaIdentityToken = oktaIdentityToken; return this; }
        private RequestBuilder oktaAccessToken(OktaAccessToken oktaAccessToken) { this.oktaAccessToken = oktaAccessToken; return this; }
        private RequestBuilder contentType(String contentType) { this.contentType = contentType; return this; }
        private RequestBuilder recursive(String recursive) {return properties(Map.of("recursive", recursive)); }
        private RequestBuilder properties(Map<String, String> properties) { this.properties.putAll(properties); return this; }
        private RequestBuilder header(String name, String value) {
            this.headers.putIfAbsent(name, new ArrayList<>());
            this.headers.get(name).add(value);
            return this;
        }

        @Override
        public Request get() {
            Request request = new Request("http://localhost:8080" + path +
                                          properties.entrySet().stream()
                                                    .map(entry -> encode(entry.getKey(), UTF_8) + "=" + encode(entry.getValue(), UTF_8))
                                                    .collect(joining("&", "?", "")),
                                          data, method);
            request.getHeaders().addAll(headers);
            request.getHeaders().put("Content-Type", contentType);
            // user and domain parameters are translated to a Principal by MockAuthorizer as we do not run HTTP filters
            if (identity != null) {
                addIdentityToRequest(request, identity);
            }
            if (oktaIdentityToken != null) {
                addOktaIdentityToken(request, oktaIdentityToken);
            }
            if (oktaAccessToken != null) {
                addOktaAccessToken(request, oktaAccessToken);
            }
            return request;
        }
    }

}
