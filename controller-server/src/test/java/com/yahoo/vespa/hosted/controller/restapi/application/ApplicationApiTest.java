// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import ai.vespa.hosted.api.MultiPartStreamer;
import ai.vespa.hosted.api.Signatures;
import com.yahoo.application.container.handler.Request;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
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
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostInfo;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringInfo;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MockTenantCost;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceAllocation;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMeteringClient;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.RotationStatusUpdater;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    private static final ZoneId TEST_ZONE = ZoneId.from(Environment.test, RegionName.from("us-east-1"));
    private static final ZoneId STAGING_ZONE = ZoneId.from(Environment.staging, RegionName.from("us-east-3"));


    private ContainerControllerTester controllerTester;
    private ContainerTester tester;

    @Before
    public void before() {
        controllerTester = new ContainerControllerTester(container, responseFiles);
        tester = controllerTester.containerTester();
    }

    @Test
    public void testApplicationApi() {
        tester.computeVersionStatus();
        tester.controller().jobController().setRunner(__ -> { }); // Avoid uncontrollable, multi-threaded job execution

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
        // PUT a user tenant
        tester.assertResponse(request("/application/v4/user", PUT).userIdentity(USER_ID),
                              "{\"message\":\"Created user 'by-myuser'\"}");
        // GET the authenticated user which now exists (with associated tenants)
        tester.assertResponse(request("/application/v4/user", GET).userIdentity(USER_ID),
                              new File("user-which-exists.json"));
        // DELETE the user
        tester.assertResponse(request("/application/v4/tenant/by-myuser", DELETE).userIdentity(USER_ID),
                              "{\"tenant\":\"by-myuser\",\"type\":\"USER\",\"applications\":[]}");
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
                              new File("instance-list.json"));
        // GET tenant applications (instances of "application1" only)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/", GET).userIdentity(USER_ID),
                              new File("instance-list.json"));

        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));

        // POST (deploy) an application to a zone - manual user deployment (includes a content hash for verification)
        MultiPartStreamer entity = createApplicationDeployData(applicationPackageInstance1, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .header("X-Content-Hash", Base64.getEncoder().encodeToString(Signatures.sha256Digest(entity::data)))
                                      .userIdentity(USER_ID),
                              new File("deploy-result.json"));


        // POST (deploy) an application to a zone. This simulates calls done by our tenant pipeline.
        ApplicationId id = ApplicationId.from("tenant1", "application1", "instance1");
        long screwdriverProjectId = 123;

        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value())); // (Necessary but not provided in this API)

        // Pipeline notifies about completed component job
        controllerTester.jobCompletion(JobType.component)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackageInstance1)
                        .submit();

        // ... systemtest
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/instance1/", POST)
                                      .data(createApplicationDeployData(Optional.empty(), false))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/instance1", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.instance1 in test.us-east-1\"}");

        controllerTester.jobCompletion(JobType.systemTest)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .submit();

        // ... staging
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/instance1/", POST)
                                      .data(createApplicationDeployData(Optional.empty(), false))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/instance1", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.instance1 in staging.us-east-3\"}");
        controllerTester.jobCompletion(JobType.stagingTest)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .submit();

        // ... prod zone
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/", POST)
                                      .data(createApplicationDeployData(Optional.empty(), false))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsCentral1)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .unsuccessful()
                        .submit();

        // POST an application deployment to a production zone - operator emergency deployment - fails since package is unknown
        entity = createApplicationDeployData(Optional.empty(),
                                             Optional.of(ApplicationVersion.from(DeploymentContext.defaultSourceRevision,
                                                                                 BuildJob.defaultBuildNumber - 1)),
                                             true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No application package found for tenant1.application1.instance1 with version 1.0.41-commit1\"}",
                              400);

        // POST an application deployment to a production zone - operator emergency deployment - works with known package
        entity = createApplicationDeployData(Optional.empty(),
                                             Optional.of(ApplicationVersion.from(DeploymentContext.defaultSourceRevision,
                                                                                 BuildJob.defaultBuildNumber)),
                                             true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              new File("deploy-result.json"));

        // POST (create) another application
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1")
                .globalServiceId("foo")
                .environment(Environment.prod)
                .region("us-west-1")
                .allow(ValidationId.globalEndpointChange)
                .build();

        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/instance/default", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance-reference-2.json"));

        ApplicationId app2 = ApplicationId.from("tenant2", "application2", "default");
        long screwdriverProjectId2 = 456;
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN_2,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(app2.application().value()));

        // Trigger upgrade and then application change
        controllerTester.controller().applications().deploymentTrigger().triggerChange(TenantAndApplicationId.from(app2), Change.of(Version.fromString("7.0")));

        controllerTester.jobCompletion(JobType.component)
                        .application(app2)
                        .projectId(screwdriverProjectId2)
                        .uploadArtifact(applicationPackage)
                        .submit();

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

        // DELETE application
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted application tenant2.application2\"}");

        // Set version 6.1 to broken to change compile version for.
        controllerTester.upgrader().overrideConfidence(Version.fromString("6.1"), VespaVersion.Confidence.broken);
        tester.computeVersionStatus();
        setDeploymentMaintainedInfo(controllerTester);
        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "us-central-1"));

        // GET tenant application deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", GET)
                                      .userIdentity(USER_ID),
                              new File("instance.json"));
        // GET an application deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1", GET)
                                      .userIdentity(USER_ID),
                              new File("deployment.json"));

        addIssues(controllerTester, TenantAndApplicationId.from("tenant1", "application1"));
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
                              "{\"message\":\"Changed deployment from 'application change to 1.0.42-commit1' to 'no change' for application 'tenant1.application1'\"}");

        // DELETE (cancel) again is a no-op
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"cancel\":\"all\"}"),
                              "{\"message\":\"No deployment in progress for application 'tenant1.application1' at this time\"}");

        // POST pinning to a given version to an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", POST)
                                      .userIdentity(USER_ID)
                                      .data("6.1.0"),
                              "{\"message\":\"Triggered pin to 6.1 for tenant1.application1\"}");
        assertTrue("Action is logged to audit log",
                   tester.controller().auditLogger().readLog().entries().stream()
                         .anyMatch(entry -> entry.resource().equals("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin")));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");

        // DELETE only the pin to a given version
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to 6.1' to 'upgrade to 6.1' for application 'tenant1.application1'\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":false}");

        // POST pinning again
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", POST)
                                      .userIdentity(USER_ID)
                                      .data("6.1"),
                              "{\"message\":\"Triggered pin to 6.1 for tenant1.application1\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");

        // DELETE only the version, but leave the pin
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/platform", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to 6.1' to 'pin to current platform' for application 'tenant1.application1'\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"pinned\":true}");

        // DELETE also the pin to a given version
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying/pin", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to current platform' to 'no change' for application 'tenant1.application1'\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploying", GET)
                                      .userIdentity(USER_ID), "{}");

        // POST a pause to a production job
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job/production-us-west-1/pause", POST)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"production-us-west-1 for tenant1.application1.instance1 paused for " + DeploymentTrigger.maxPause + "\"}");

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
        tester.serviceRegistry().configServerMock().nodeRepository().addFixedNodes(ZoneId.from("prod", "us-central-1"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/instance1/restart?hostname=hostA", POST)
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
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.instance1 in dev.us-west-1\"}");

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
        tester.serviceRegistry().routingGeneratorMock().putEndpoints(new DeploymentId(ApplicationId.from("tenant1", "application1", "default"), ZoneId.from("prod", "us-central-1")),
                                                                     List.of(new RoutingEndpoint("https://us-central-1.prod.default", "host", false, "upstream")));
        tester.serviceRegistry().routingGeneratorMock().putEndpoints(new DeploymentId(ApplicationId.from("tenant1", "application1", "my-user"), ZoneId.from("dev", "us-east-1")),
                                                                     List.of(new RoutingEndpoint("https://us-east-1.dev.my-user", "host", false, "upstream")));
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

        // POST an application package to start a deployment to dev
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/deploy/dev-us-east-1", POST)
                                      .userIdentity(USER_ID)
                                      .data(createApplicationDeployData(applicationPackage, false)),
                              new File("deployment-job-accepted.json"));

        // POST an application package is allowed under user instance
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/otheruser/deploy/dev-us-east-1", POST)
                             .userIdentity(OTHER_USER_ID)
                             .data(createApplicationDeployData(applicationPackage, false)),
                              new File("deployment-job-accepted-2.json"));

        // DELETE a dev deployment is allowed under user instance
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/otheruser/environment/dev/region/us-east-1", DELETE)
                                      .userIdentity(OTHER_USER_ID),
                              "{\"message\":\"Deactivated tenant1.application1.otheruser in dev.us-east-1\"}");

        // POST an application package and a test jar, submitting a new application for internal pipeline deployment.
        // First attempt does not have an Athenz service definition in deployment spec, and is accepted.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(applicationPackage)),
                              "{\"message\":\"Application package version: 1.0.43-d00d, source revision of repository 'repo', branch 'master' with commit 'd00d', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        // GET application package
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              (response) -> {
                                  assertEquals("attachment; filename=\"tenant1.application1-build43.zip\"", response.getHeaders().getFirst("Content-Disposition"));
                                  assertArrayEquals(applicationPackage.zippedContent(), response.getBody());
                              },
                              200);

        // Second attempt has a service under a different domain than the tenant of the application, and fails.
        ApplicationPackage packageWithServiceForWrongDomain = new ApplicationPackageBuilder()
                .instances("instance1")
                .environment(Environment.prod)
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from(ATHENZ_TENANT_DOMAIN_2.getName()), AthenzService.from("service"))
                .region("us-west-1")
                .build();
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN_2, "service"), true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(packageWithServiceForWrongDomain)),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz domain in deployment.xml: [domain2] must match tenant domain: [domain1]\"}", 400);

        // Third attempt finally has a service under the domain of the tenant, and succeeds.
        ApplicationPackage packageWithService = new ApplicationPackageBuilder()
                .instances("instance1")
                .globalServiceId("foo")
                .environment(Environment.prod)
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from(ATHENZ_TENANT_DOMAIN.getName()), AthenzService.from("service"))
                .region("us-west-1")
                .build();
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"), true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(packageWithService)),
                              "{\"message\":\"Application package version: 1.0.44-d00d, source revision of repository 'repo', branch 'master' with commit 'd00d', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        // GET last submitted application package
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              (response) -> {
                                  assertEquals("attachment; filename=\"tenant1.application1-build44.zip\"", response.getHeaders().getFirst("Content-Disposition"));
                                  assertArrayEquals(packageWithService.zippedContent(), response.getBody());
                              },
                              200);

        // GET application package for previous build
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package?build=43", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              (response) -> {
                                  assertEquals("attachment; filename=\"tenant1.application1-build43.zip\"", response.getHeaders().getFirst("Content-Disposition"));
                                  assertArrayEquals(applicationPackage.zippedContent(), response.getBody());
                              },
                              200);

        // Fourth attempt has a wrong content hash in a header, and fails.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .header("X-Content-Hash", "not/the/right/hash")
                                      .data(createApplicationSubmissionData(packageWithService)),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Value of X-Content-Hash header does not match computed content hash\"}", 400);

        // Fifth attempt has the right content hash in a header, and succeeds.
        MultiPartStreamer streamer = createApplicationSubmissionData(packageWithService);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .header("X-Content-Hash", Base64.getEncoder().encodeToString(Signatures.sha256Digest(streamer::data)))
                                      .data(streamer),
                              "{\"message\":\"Application package version: 1.0.45-d00d, source revision of repository 'repo', branch 'master' with commit 'd00d', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        // Sixth attempt has a multi-instance deployment spec, and fails.
        ApplicationPackage multiInstanceSpec = new ApplicationPackageBuilder()
                .instances("instance1,instance2")
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(multiInstanceSpec)),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Only single-instance deployment specs are currently supported\"}", 400);

        ApplicationId app1 = ApplicationId.from("tenant1", "application1", "instance1");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/jobreport", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(asJson(DeploymentJobs.JobReport.ofComponent(app1,
                                                                                        1234,
                                                                                        123,
                                                                                        Optional.empty(),
                                                                                        DeploymentContext.defaultSourceRevision))),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"" + app1 + " is set up to be deployed from internally," +
                              " and no longer accepts submissions from Screwdriver v3 jobs. If you need to revert " +
                              "to the old pipeline, please file a ticket at yo/vespa-support and request this.\"}",
                              400);

        // GET deployment job overview, after triggering system and staging test jobs.
        assertEquals(2, tester.controller().applications().deploymentTrigger().triggerReadyJobs());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/job", GET)
                                      .userIdentity(USER_ID),
                              new File("jobs.json"));

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
                              "{\"message\":\"Aborting run 1 of staging-test for tenant1.application1.instance1\"}");

        // DELETE submission to unsubscribe from continuous deployment.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/submit", DELETE)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"message\":\"Unregistered 'tenant1.application1' from internal deployment pipeline.\"}");

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/jobreport", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(asJson(DeploymentJobs.JobReport.ofComponent(app1,
                                                                                        1234,
                                                                                        123,
                                                                                        Optional.empty(),
                                                                                        DeploymentContext.defaultSourceRevision))),
                              "{\"message\":\"ok\"}");

        // PUT (create) the authenticated user
        byte[] data = new byte[0];
        tester.assertResponse(request("/application/v4/user?user=new_user&domain=by", PUT)
                                      .data(data)
                                      .userIdentity(new UserId("new_user")), // Normalized to by-new-user by API
                              new File("create-user-response.json"));

        // GET user lists only tenants for the authenticated user
        tester.assertResponse(request("/application/v4/user", GET)
                                      .userIdentity(new UserId("other_user")),
                              "{\"user\":\"other_user\",\"tenants\":[],\"tenantExists\":false}");

        // OPTIONS return 200 OK
        tester.assertResponse(request("/application/v4/", Request.Method.OPTIONS)
                                      .userIdentity(USER_ID),
                              "");

        // DELETE all instances under an application to delete the application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant1.application1.default\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/my-user", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant1.application1.my-user\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant1.application1.instance1\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/otheruser", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant1.application1.otheruser\"}");

        // DELETE a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE).userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("tenant-without-applications.json"));
    }

    private void addIssues(ContainerControllerTester tester, TenantAndApplicationId id) {
        tester.controller().applications().lockApplicationOrThrow(id, application ->
                tester.controller().applications().store(application.withDeploymentIssueId(IssueId.from("123"))
                                                                    .withOwnershipIssueId(IssueId.from("321"))
                                                                    .withOwner(User.from("owner-username"))));
    }

    @Test
    public void testRotationOverride() {
        // Setup
        tester.computeVersionStatus();
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1")
                .globalServiceId("foo")
                .region("us-west-1")
                .region("us-east-3")
                .build();

        // Create tenant and deploy
        ApplicationId id = createTenantAndApplication();
        long projectId = 1;
        MultiPartStreamer deployData = createApplicationDeployData(Optional.of(applicationPackage), false);
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 100);

        // us-west-1
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsWest1)
                        .application(id)
                        .projectId(projectId)
                        .submit();
        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "us-west-1"));

        // Invalid application fails
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"tenant2.application2 not found\"}",
                              400);

        // Invalid deployment fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-east-3/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"application 'tenant1.application1.instance1' has no deployment in prod.us-east-3\"}",
                              404);

        // Change status of non-existing deployment fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-east-3/global-rotation/override", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"application 'tenant1.application1.instance1' has no deployment in prod.us-east-3\"}",
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

        // DELETE global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation/override", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              new File("global-rotation-delete.json"));
    }

    @Test
    public void multiple_endpoints() {
        // Setup
        tester.computeVersionStatus();
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
        ApplicationId id = createTenantAndApplication();
        long projectId = 1;
        MultiPartStreamer deployData = createApplicationDeployData(Optional.empty(), false);
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 100);
        for (var job : List.of(JobType.productionUsWest1, JobType.productionUsEast3, JobType.productionEuWest1)) {
            tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/" + job.zone(SystemName.main).region().value() + "/deploy", POST)
                                          .data(deployData)
                                          .screwdriverIdentity(SCREWDRIVER_ID),
                                  new File("deploy-result.json"));
            controllerTester.jobCompletion(job)
                            .application(id)
                            .projectId(projectId)
                            .submit();
        }
        setZoneInRotation("rotation-fqdn-2", ZoneId.from("prod", "us-west-1"));
        setZoneInRotation("rotation-fqdn-2", ZoneId.from("prod", "us-east-3"));
        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "eu-west-1"));

        // GET global rotation status without specifying endpointId fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"application 'tenant1.application1.instance1' has multiple rotations. Query parameter 'endpointId' must be given\"}",
                              400);

        // GET global rotation status for us-west-1 in default endpoint
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation?endpointId=default", GET)
                                      .userIdentity(USER_ID),
                              "{\"bcpStatus\":{\"rotationStatus\":\"IN\"}}",
                              200);

        // GET global rotation status for us-west-1 in eu endpoint
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/global-rotation?endpointId=eu", GET)
                                      .userIdentity(USER_ID),
                              "{\"bcpStatus\":{\"rotationStatus\":\"UNKNOWN\"}}",
                              200);

        // GET global rotation status for eu-west-1 in eu endpoint
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/eu-west-1/global-rotation?endpointId=eu", GET)
                                      .userIdentity(USER_ID),
                              "{\"bcpStatus\":{\"rotationStatus\":\"IN\"}}",
                              200);
    }

    @Test
    public void testDeployDirectly() {
        // Setup
        tester.computeVersionStatus();
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
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId("application1"));

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
        tester.upgradeSystem(tester.controller().versionStatus().controllerVersion().get().versionNumber());
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
    public void testSortsDeploymentsAndJobs() {
        tester.computeVersionStatus();

        // Deploy
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1")
                .region("us-east-3")
                .build();
        ApplicationId id = createTenantAndApplication();
        long projectId = 1;
        MultiPartStreamer deployData = createApplicationDeployData(Optional.empty(), false);
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 100);

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-east-3/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsEast3)
                        .application(id)
                        .projectId(projectId)
                        .submit();

        // New zone is added before us-east-3
        applicationPackage = new ApplicationPackageBuilder()
                .instances("instance1")
                .globalServiceId("foo")
                // These decides the ordering of deploymentJobs and instances in the response
                .region("us-west-1")
                .region("us-east-3")
                .build();
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 101);

        // us-west-1
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-west-1/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsWest1)
                        .application(id)
                        .projectId(projectId)
                        .submit();

        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "us-west-1"));

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1/environment/prod/region/us-east-3/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsEast3)
                        .application(id)
                        .projectId(projectId)
                        .submit();

        setDeploymentMaintainedInfo(controllerTester);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/instance1", GET)
                                      .userIdentity(USER_ID),
                              new File("instance-without-change-multiple-deployments.json"));
    }

    @Test
    public void testMeteringResponses() {
        MockMeteringClient mockMeteringClient = (MockMeteringClient) controllerTester.containerTester().serviceRegistry().meteringService();

        // Mock response for MeteringClient
        ResourceAllocation currentSnapshot = new ResourceAllocation(1, 2, 3);
        ResourceAllocation thisMonth = new ResourceAllocation(12, 24, 1000);
        ResourceAllocation lastMonth = new ResourceAllocation(24, 48, 2000);
        ApplicationId applicationId = ApplicationId.from("doesnotexist", "doesnotexist", "default");
        Map<ApplicationId, List<ResourceSnapshot>> snapshotHistory = Map.of(applicationId, List.of(
                new ResourceSnapshot(applicationId, 1, 2,3, Instant.ofEpochMilli(123), ZoneId.defaultId()),
                new ResourceSnapshot(applicationId, 1, 2,3, Instant.ofEpochMilli(246), ZoneId.defaultId()),
                new ResourceSnapshot(applicationId, 1, 2,3, Instant.ofEpochMilli(492), ZoneId.defaultId())));

        mockMeteringClient.setMeteringInfo(new MeteringInfo(thisMonth, lastMonth, currentSnapshot, snapshotHistory));

        tester.assertResponse(request("/application/v4/tenant/doesnotexist/application/doesnotexist/metering", GET)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              new File("instance1-metering.json"));
    }

    @Test
    public void testTenantCostResponse() {
        ApplicationId applicationId = createTenantAndApplication();
        MockTenantCost mockTenantCost = (MockTenantCost) controllerTester.containerTester().serviceRegistry().tenantCost();

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
        tester.computeVersionStatus();
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

        ConfigServerMock configServer = serviceRegistry().configServerMock();
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE, null));

        // GET non-existent application package
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"No application package has been submitted for 'tenant1.application1'\"}",
                              404);

        // GET non-existent application package of specific build
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package?build=42", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"No application package found for 'tenant1.application1' with build number 42\"}",
                              404);

        // GET non-existent application package of invalid build
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/package?build=foobar", GET).userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid build number: For input string: \\\"foobar\\\"\"}",
                              400);
        
        // POST (deploy) an application with an invalid application package
        MultiPartStreamer entity = createApplicationDeployData(applicationPackageInstance1, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-failure.json"), 400);

        // POST (deploy) an application without available capacity
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", ConfigServerException.ErrorCode.OUT_OF_CAPACITY, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-out-of-capacity.json"), 400);

        // POST (deploy) an application where activation fails
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to activate application", ConfigServerException.ErrorCode.ACTIVATION_CONFLICT, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/instance1/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-activation-conflict.json"), 409);

        // POST (deploy) an application where we get an internal server error
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Internal server error", ConfigServerException.ErrorCode.INTERNAL_SERVER_ERROR, null));
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

        // Deleting the application when more than one instance is present is forbidden
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not delete application; more than one instance present: [tenant1.application1, tenant1.application1.instance1]\"}",
                              400);

        // Deleting one instance is OK
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default", DELETE)
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                              "{\"message\":\"Deleted instance tenant1.application1.default\"}",
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
    public void deployment_fails_on_illegal_domain_in_deployment_spec() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("another.domain"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        long screwdriverProjectId = 123;
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(new AthenzDomain("another.domain"), "service"), true);

        Application application = controllerTester.createApplication(ATHENZ_TENANT_DOMAIN.getName(), "tenant1", "application1", "default");
        ScrewdriverId screwdriverId = new ScrewdriverId(Long.toString(screwdriverProjectId));
        controllerTester.authorize(ATHENZ_TENANT_DOMAIN, screwdriverId, ApplicationAction.deploy, application.id());

        controllerTester.jobCompletion(JobType.component)
                        .application(application)
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackage)
                        .submit();
                              
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, false))
                                      .screwdriverIdentity(screwdriverId),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz domain in deployment.xml: [another.domain] must match tenant domain: [domain1]\"}",
                              400);

    }

    @Test
    public void deployment_succeeds_when_correct_domain_is_used() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        long screwdriverProjectId = 123;
        ScrewdriverId screwdriverId = new ScrewdriverId(Long.toString(screwdriverProjectId));

        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"), true);

        Application application = controllerTester.createApplication(ATHENZ_TENANT_DOMAIN.getName(), "tenant1", "application1", "default");
        controllerTester.authorize(ATHENZ_TENANT_DOMAIN, screwdriverId, ApplicationAction.deploy, application.id());

        // Allow systemtest to succeed by notifying completion of component
        controllerTester.jobCompletion(JobType.component)
                        .application(application)
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackage)
                        .submit();
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, false))
                                      .screwdriverIdentity(screwdriverId),
                              new File("deploy-result.json"));

    }

    @Test
    public void deployment_fails_for_personal_tenants_when_athenzdomain_specified_and_user_not_admin() {
        // Setup
        tester.computeVersionStatus();
        UserId tenantAdmin = new UserId("tenant-admin");
        UserId userId = new UserId("new-user");
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, tenantAdmin);
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"), true);

        // Create tenant
        // PUT (create) the authenticated user
        byte[] data = new byte[0];
        tester.assertResponse(request("/application/v4/user?user=new_user&domain=by", PUT)
                                      .data(data)
                                      .userIdentity(userId), // Normalized to by-new-user by API
                              new File("create-user-response.json"));

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.dev)
                .region("us-west-1")
                .build();

        // POST (deploy) an application to a dev zone
        String expectedResult="{\"error-code\":\"BAD_REQUEST\",\"message\":\"User user.new-user is not allowed to launch services in Athenz domain domain1. Please reach out to the domain admin.\"}";
        MultiPartStreamer entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/by-new-user/application/application1/environment/dev/region/us-west-1/instance/default", POST)
                                      .data(entity)
                                      .userIdentity(userId),
                              expectedResult,
                              400);

    }

    @Test
    public void deployment_succeeds_for_personal_tenants_when_user_is_tenant_admin() {

        // Setup
        tester.computeVersionStatus();
        UserId tenantAdmin = new UserId("new_user");
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, tenantAdmin);
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"), true);

        // Create tenant
        // PUT (create) the authenticated user
        byte[] data = new byte[0];
        tester.assertResponse(request("/application/v4/user?user=new_user&domain=by", PUT)
                                      .data(data)
                                      .userIdentity(tenantAdmin), // Normalized to by-new-user by API
                              new File("create-user-response.json"));

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.dev)
                .region("us-west-1")
                .build();

        // POST (deploy) an application to a dev zone
        MultiPartStreamer entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/by-new-user/application/application1/environment/dev/region/us-west-1/instance/default", POST)
                                      .data(entity)
                                      .userIdentity(tenantAdmin),
                              new File("deploy-result.json"));
    }

    @Test
    public void deployment_fails_when_athenz_service_cannot_be_launched() {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        long screwdriverProjectId = 123;
        ScrewdriverId screwdriverId = new ScrewdriverId(Long.toString(screwdriverProjectId));

        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"), false);

        Application application = controllerTester.createApplication(ATHENZ_TENANT_DOMAIN.getName(), "tenant1", "application1", "default");
        controllerTester.authorize(ATHENZ_TENANT_DOMAIN, screwdriverId, ApplicationAction.deploy, application.id());

        // Allow systemtest to succeed by notifying completion of system test
        controllerTester.jobCompletion(JobType.component)
                        .application(application)
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackage)
                        .submit();

        String expectedResult="{\"error-code\":\"BAD_REQUEST\",\"message\":\"Not allowed to launch Athenz service domain1.service\"}";
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, false))
                                      .screwdriverIdentity(screwdriverId),
                              expectedResult,
                              400);

    }

    @Test
    public void redeployment_succeeds_when_not_specifying_versions_or_application_package() {
        // Setup
        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));
        tester.computeVersionStatus();

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("domain1"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        long screwdriverProjectId = 123;
        ScrewdriverId screwdriverId = new ScrewdriverId(Long.toString(screwdriverProjectId));

        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);
        configureAthenzIdentity(new com.yahoo.vespa.athenz.api.AthenzService(ATHENZ_TENANT_DOMAIN, "service"), true);

        Application application = controllerTester.createApplication(ATHENZ_TENANT_DOMAIN.getName(), "tenant1", "application1", "default");
        controllerTester.authorize(ATHENZ_TENANT_DOMAIN, screwdriverId, ApplicationAction.deploy, application.id());

        // Allow systemtest to succeed by notifying completion of component
        controllerTester.jobCompletion(JobType.component)
                .application(application)
                .projectId(screwdriverProjectId)
                .uploadArtifact(applicationPackage)
                .submit();
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                        .data(createApplicationDeployData(applicationPackage, false))
                        .screwdriverIdentity(screwdriverId),
                new File("deploy-result.json"));

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                        .data(createApplicationDeployData(Optional.empty(), true))
                        .userIdentity(HOSTED_VESPA_OPERATOR),
                new File("deploy-result.json"));
    }


    @Test
    public void testJobStatusReporting() {
        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));
        tester.computeVersionStatus();
        long projectId = 1;
        Application app = controllerTester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .build();

        Version vespaVersion = new Version("6.1"); // system version from mock config server client

        BuildJob job = new BuildJob(report -> notifyCompletion(report, controllerTester), controllerTester.containerTester().serviceRegistry().artifactRepositoryMock())
                .application(app)
                .projectId(projectId);
        job.type(JobType.component).uploadArtifact(applicationPackage).submit();
        controllerTester.deploy(app.id().defaultInstance(), applicationPackage, TEST_ZONE);
        ((ManualClock) controllerTester.controller().clock()).advance(Duration.ofSeconds(1));
        job.type(JobType.systemTest).submit();

        // Notifying about job started not by the controller fails
        var request = request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                .data(asJson(job.type(JobType.systemTest).report()))
                .userIdentity(HOSTED_VESPA_OPERATOR);
        tester.assertResponse(request, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Notified of completion " +
                                       "of system-test for tenant1.application1, but that has not been triggered; last was " +
                                       controllerTester.controller().applications().requireInstance(app.id().defaultInstance()).deploymentJobs().jobStatus().get(JobType.systemTest).lastTriggered().get().at() + "\"}", 400);

        // Notifying about unknown job fails
        request = request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                .data(asJson(job.type(JobType.productionUsEast3).report()))
                .userIdentity(HOSTED_VESPA_OPERATOR);
        tester.assertResponse(request, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Notified of completion " +
                                       "of production-us-east-3 for tenant1.application1, but that has not been triggered; last was never\"}",
                              400);

        // ... and assert it was recorded
        JobStatus recordedStatus =
                tester.controller().applications().getInstance(app.id().defaultInstance()).get().deploymentJobs().jobStatus().get(JobType.systemTest);

        assertNotNull("Status was recorded", recordedStatus);
        assertTrue(recordedStatus.isSuccess());
        assertEquals(vespaVersion, recordedStatus.lastCompleted().get().platform());

        recordedStatus =
                tester.controller().applications().getInstance(app.id().defaultInstance()).get().deploymentJobs().jobStatus().get(JobType.productionApNortheast2);
        assertNull("Status of never-triggered jobs is empty", recordedStatus);
        assertTrue("All jobs have been run", tester.controller().applications().deploymentTrigger().jobsToRun().isEmpty());
    }

    @Test
    public void testJobStatusReportingOutOfCapacity() {
        controllerTester.containerTester().computeVersionStatus();

        long projectId = 1;
        Application app = controllerTester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-central-1")
                .build();

        // Report job failing with out of capacity
        BuildJob job = new BuildJob(report -> notifyCompletion(report, controllerTester), controllerTester.containerTester().serviceRegistry().artifactRepositoryMock())
                .application(app)
                .projectId(projectId);
        job.type(JobType.component).uploadArtifact(applicationPackage).submit();

        controllerTester.deploy(app.id().defaultInstance(), applicationPackage, TEST_ZONE);
        job.type(JobType.systemTest).submit();
        controllerTester.deploy(app.id().defaultInstance(), applicationPackage, STAGING_ZONE);
        job.type(JobType.stagingTest).error(DeploymentJobs.JobError.outOfCapacity).submit();

        // Appropriate error is recorded
        JobStatus jobStatus = tester.controller().applications().getInstance(app.id().defaultInstance()).get()
                                    .deploymentJobs()
                                    .jobStatus()
                                    .get(JobType.stagingTest);
        assertFalse(jobStatus.isSuccess());
        assertEquals(DeploymentJobs.JobError.outOfCapacity, jobStatus.jobError().get());
    }

    @Test
    public void applicationWithRoutingPolicy() {
        Application app = controllerTester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        controllerTester.deployCompletely(app, applicationPackage, 1, false);
        RoutingPolicy policy = new RoutingPolicy(app.id().defaultInstance(),
                                                 ClusterSpec.Id.from("default"),
                                                 ZoneId.from(Environment.prod, RegionName.from("us-west-1")),
                                                 HostName.from("lb-0-canonical-name"),
                                                 Optional.of("dns-zone-1"), Set.of(EndpointId.of("c0")));
        tester.controller().curator().writeRoutingPolicies(app.id().defaultInstance(), Set.of(policy));

        // GET application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default", GET)
                                      .userIdentity(USER_ID),
                              new File("instance-with-routing-policy.json"));

        // GET deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default", GET)
                                      .userIdentity(USER_ID),
                              new File("deployment-with-routing-policy.json"));
    }

    private void notifyCompletion(DeploymentJobs.JobReport report, ContainerControllerTester tester) {
        assertResponse(request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                               .userIdentity(HOSTED_VESPA_OPERATOR)
                               .data(asJson(report))
                               .get(),
                       200, "{\"message\":\"ok\"}");
        tester.controller().applications().deploymentTrigger().triggerReadyJobs();
    }

    private static byte[] asJson(DeploymentJobs.JobReport report) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setLong("projectId", report.projectId());
        cursor.setString("jobName", report.jobType().jobName());
        cursor.setLong("buildNumber", report.buildNumber());
        report.jobError().ifPresent(jobError -> cursor.setString("jobError", jobError.name()));
        report.version().flatMap(ApplicationVersion::source).ifPresent(sr -> {
            Cursor sourceRevision = cursor.setObject("sourceRevision");
            sourceRevision.setString("repository", sr.repository());
            sourceRevision.setString("branch", sr.branch());
            sourceRevision.setString("commit", sr.commit());
        });
        cursor.setString("tenant", report.applicationId().tenant().value());
        cursor.setString("application", report.applicationId().application().value());
        cursor.setString("instance", report.applicationId().instance().value());
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    private MultiPartStreamer createApplicationSubmissionData(ApplicationPackage applicationPackage) {
        return new MultiPartStreamer().addJson(EnvironmentResource.SUBMIT_OPTIONS, "{\"repository\":\"repo\",\"branch\":\"master\",\"commit\":\"d00d\",\"authorEmail\":\"a@b\"}")
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
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        AthenzDbMock.Domain domainMock = mock.getSetup().getOrCreateDomain(domain);
        domainMock.markAsVespaTenant();
        domainMock.admin(AthenzUser.fromUserId(userId.id()));
    }

    /**
     * Mock athenz service identity configuration. Simulates that configserver is allowed to launch a service
     */
    private void configureAthenzIdentity(com.yahoo.vespa.athenz.api.AthenzService service, boolean allowLaunch) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                                                                          .getComponent(AthenzClientFactoryMock.class.getName());
        AthenzDbMock.Domain domainMock = mock.getSetup().domains.computeIfAbsent(service.getDomain(), AthenzDbMock.Domain::new);
        domainMock.services.put(service.getName(), new AthenzDbMock.Service(allowLaunch));
    }


    /**
     * In production this happens outside hosted Vespa, so there is no API for it and we need to reach down into the
     * mock setup to replicate the action.
     */
    private void addScrewdriverUserToDeployRole(ScrewdriverId screwdriverId,
                                                AthenzDomain domain,
                                                com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId applicationId) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        AthenzIdentity screwdriverIdentity = HostedAthenzIdentities.from(screwdriverId);
        AthenzDbMock.Application athenzApplication = mock.getSetup().domains.get(domain).applications.get(applicationId);
        athenzApplication.addRoleMember(ApplicationAction.deploy, screwdriverIdentity);
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
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID, ATHENZ_TENANT_DOMAIN,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId("application1"));

        return ApplicationId.from("tenant1", "application1", "instance1");
    }

    private void startAndTestChange(ContainerControllerTester controllerTester, ApplicationId application,
                                    long projectId, ApplicationPackage applicationPackage,
                                    MultiPartStreamer deployData, long buildNumber) {
        ContainerTester tester = controllerTester.containerTester();

        // Trigger application change
        controllerTester.containerTester().serviceRegistry().artifactRepositoryMock()
                        .put(application, applicationPackage,"1.0." + buildNumber + "-commit1");
        controllerTester.jobCompletion(JobType.component)
                        .application(application)
                        .projectId(projectId)
                        .buildNumber(buildNumber)
                        .submit();

        // system-test
        String testPath = String.format("/application/v4/tenant/%s/application/%s/instance/%s/environment/test/region/us-east-1",
                application.tenant().value(), application.application().value(), application.instance().value());
        tester.assertResponse(request(testPath, POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request(testPath, DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                "{\"message\":\"Deactivated " + application + " in test.us-east-1\"}");
        controllerTester.jobCompletion(JobType.systemTest)
                        .application(application)
                        .projectId(projectId)
                        .submit();

        // staging
        String stagingPath = String.format("/application/v4/tenant/%s/application/%s/instance/%s/environment/staging/region/us-east-3",
                application.tenant().value(), application.application().value(), application.instance().value());
        tester.assertResponse(request(stagingPath, POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request(stagingPath, DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                "{\"message\":\"Deactivated " + application + " in staging.us-east-3\"}");
        controllerTester.jobCompletion(JobType.stagingTest)
                        .application(application)
                        .projectId(projectId)
                        .submit();
    }

    /**
     * Cluster info, utilization and application and deployment metrics are maintained async by maintainers.
     *
     * This sets these values as if the maintainers has been ran.
     */
    private void setDeploymentMaintainedInfo(ContainerControllerTester controllerTester) {
        for (Application application : controllerTester.controller().applications().asList()) {
            controllerTester.controller().applications().lockApplicationOrThrow(application.id(), lockedApplication -> {
                lockedApplication = lockedApplication.with(new ApplicationMetrics(0.5, 0.7));

                for (Instance instance : application.instances().values()) {
                    for (Deployment deployment : instance.deployments().values()) {
                        Map<ClusterSpec.Id, ClusterInfo> clusterInfo = new HashMap<>();
                        List<String> hostnames = new ArrayList<>();
                        hostnames.add("host1");
                        hostnames.add("host2");
                        clusterInfo.put(ClusterSpec.Id.from("cluster1"),
                                        new ClusterInfo("flavor1", 37, 2, 4, 50,
                                                        ClusterSpec.Type.content, hostnames));
                        DeploymentMetrics metrics = new DeploymentMetrics(1, 2, 3, 4, 5,
                                                                          Optional.of(Instant.ofEpochMilli(123123)), Map.of());

                        lockedApplication = lockedApplication.with(instance.name(),
                                                                   lockedInstance -> lockedInstance.withClusterInfo(deployment.zone(), clusterInfo)
                                                                                                   .with(deployment.zone(), metrics)
                                                                                                   .recordActivityAt(Instant.parse("2018-06-01T10:15:30.00Z"), deployment.zone()));
                    }
                    controllerTester.controller().applications().store(lockedApplication);
                }
            });
        }
    }

    private ServiceRegistryMock serviceRegistry() {
        return (ServiceRegistryMock) tester.container().components().getComponent(ServiceRegistryMock.class.getName());
    }

    private void setZoneInRotation(String rotationName, ZoneId zone) {
        serviceRegistry().globalRoutingServiceMock().setStatus(rotationName, zone, com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus.IN);
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
        serviceRegistry().contactRetrieverMock().addContact(p, new Contact(URI.create("www.issues.tld/" + p.id()),
                                                                           URI.create("www.contacts.tld/" + p.id()),
                                                                           URI.create("www.properties.tld/" + p.id()),
                                                                           List.of(Collections.singletonList("alice"),
                                                                                   Collections.singletonList("bob")),
                                                                           "queue", Optional.empty()));
    }

    private static class RequestBuilder implements Supplier<Request> {

        private final String path;
        private final Request.Method method;
        private byte[] data = new byte[0];
        private AthenzIdentity identity;
        private OktaIdentityToken oktaIdentityToken;
        private OktaAccessToken oktaAccessToken;
        private String contentType = "application/json";
        private Map<String, List<String>> headers = new HashMap<>();
        private String recursive;

        private RequestBuilder(String path, Request.Method method) {
            this.path = path;
            this.method = method;
        }

        private RequestBuilder data(byte[] data) { this.data = data; return this; }
        private RequestBuilder data(String data) { return data(data.getBytes(StandardCharsets.UTF_8)); }
        private RequestBuilder data(MultiPartStreamer streamer) {
            return Exceptions.uncheck(() -> data(streamer.data().readAllBytes()).contentType(streamer.contentType()));
        }

        private RequestBuilder userIdentity(UserId userId) { this.identity = HostedAthenzIdentities.from(userId); return this; }
        private RequestBuilder screwdriverIdentity(ScrewdriverId screwdriverId) { this.identity = HostedAthenzIdentities.from(screwdriverId); return this; }
        private RequestBuilder oktaIdentityToken(OktaIdentityToken oktaIdentityToken) { this.oktaIdentityToken = oktaIdentityToken; return this; }
        private RequestBuilder oktaAccessToken(OktaAccessToken oktaAccessToken) { this.oktaAccessToken = oktaAccessToken; return this; }
        private RequestBuilder contentType(String contentType) { this.contentType = contentType; return this; }
        private RequestBuilder recursive(String recursive) { this.recursive = recursive; return this; }
        private RequestBuilder header(String name, String value) {
            this.headers.putIfAbsent(name, new ArrayList<>());
            this.headers.get(name).add(value);
            return this;
        }

        @Override
        public Request get() {
            Request request = new Request("http://localhost:8080" + path +
                                          // user and domain parameters are translated to a Principal by MockAuthorizer as we do not run HTTP filters
                                          (recursive == null ? "" : "?recursive=" + recursive),
                                          data, method);
            request.getHeaders().addAll(headers);
            request.getHeaders().put("Content-Type", contentType);
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
