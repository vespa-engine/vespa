// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.application.container.handler.Request;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.EnvironmentResource;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockContactRetriever;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.integration.ConfigServerMock;
import com.yahoo.vespa.hosted.controller.integration.MetricsServiceMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import static com.yahoo.application.container.handler.Request.Method.DELETE;
import static com.yahoo.application.container.handler.Request.Method.GET;
import static com.yahoo.application.container.handler.Request.Method.PATCH;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.application.container.handler.Request.Method.PUT;
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

    private static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
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
    private static final UserId HOSTED_VESPA_OPERATOR = new UserId("johnoperator");
    private static final OktaAccessToken OKTA_AT = new OktaAccessToken("dummy");
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

        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID); // (Necessary but not provided in this API)

        // GET API root
        tester.assertResponse(request("/application/v4/", GET).userIdentity(USER_ID),
                              new File("root.json"));
        // POST (add) a tenant without property ID
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT),
                              new File("tenant-without-applications.json"));
        // PUT (modify) a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT)
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


        // Add another Athens domain, so we can try to create more tenants
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN_2, USER_ID); // New domain to test tenant w/property ID
        // Add property info for that property id, as well, in the mock organization.
        registerContact(1234);

        // POST (add) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}"),
                              new File("tenant-without-applications-with-id.json"));
        // PUT (modify) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", PUT)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}"),
                              new File("tenant-without-applications-with-id.json"));
        // GET a tenant with property ID and contact information
        updateContactInformation();
        tester.assertResponse(request("/application/v4/tenant/tenant2", GET).userIdentity(USER_ID),
                              new File("tenant-with-contact-info.json"));

        // POST (create) an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("application-reference.json"));
        // GET a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", GET).userIdentity(USER_ID),
                              new File("tenant-with-application.json"));

        // GET tenant applications
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/", GET).userIdentity(USER_ID),
                              new File("application-list.json"));

        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));

        // POST (deploy) an application to a zone - manual user deployment
        HttpEntity entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-result.json"));


        // POST (deploy) an application to a zone. This simulates calls done by our tenant pipeline.
        ApplicationId id = ApplicationId.from("tenant1", "application1", "default");
        long screwdriverProjectId = 123;

        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value())); // (Necessary but not provided in this API)

        // Pipeline notifies about completed component job
        controllerTester.jobCompletion(JobType.component)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackage)
                        .submit();

        // ... systemtest
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(Optional.empty(), false))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default");

        controllerTester.jobCompletion(JobType.systemTest)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .submit();

        // ... staging
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default/", POST)
                                      .data(createApplicationDeployData(Optional.empty(), false))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default");
        controllerTester.jobCompletion(JobType.stagingTest)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .submit();

        // ... prod zone
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/", POST)
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
                                             Optional.of(ApplicationVersion.from(BuildJob.defaultSourceRevision,
                                                                                 BuildJob.defaultBuildNumber - 1)),
                                             true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No application package found for tenant1.application1 with version 1.0.41-commit1\"}",
                              400);

        // POST an application deployment to a production zone - operator emergency deployment - works with known package
        entity = createApplicationDeployData(Optional.empty(),
                                             Optional.of(ApplicationVersion.from(BuildJob.defaultSourceRevision,
                                                                                 BuildJob.defaultBuildNumber)),
                                             true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/", POST)
                                      .data(entity)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              new File("deploy-result.json"));

        // POST (create) another application
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .build();

        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("application-reference-2.json"));

        ApplicationId app2 = ApplicationId.from("tenant2", "application2", "default");
        long screwdriverProjectId2 = 456;
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN_2,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(app2.application().value()));

        // Trigger upgrade and then application change
        controllerTester.controller().applications().deploymentTrigger().triggerChange(app2, Change.of(Version.fromString("7.0")));

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

        // PATCH in a pem deploy key
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", PATCH)
                                      .userIdentity(USER_ID)
                                      .data("{\"pemDeployKey\":\"-----BEGIN PUBLIC KEY-----\n∠( ᐛ 」∠)＿\n-----END PUBLIC KEY-----\"}"),
                              "{\"message\":\"Set pem deploy key to -----BEGIN PUBLIC KEY-----\\n∠( ᐛ 」∠)＿\\n-----END PUBLIC KEY-----\"}");

        // GET an application with a major version override
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", GET)
                                      .userIdentity(USER_ID),
                              new File("application2-with-patches.json"));

        // PATCH in removal of the application major version override removal
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", PATCH)
                                      .userIdentity(USER_ID)
                                      .data("{\"majorVersion\":null}"),
                              "{\"message\":\"Set major version to empty\"}");

        // PATCH in removal of the pem deploy key
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", PATCH)
                                      .userIdentity(USER_ID)
                                      .data("{\"pemDeployKey\":null}"),
                              "{\"message\":\"Set pem deploy key to empty\"}");

        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", GET)
                                      .userIdentity(USER_ID),
                              new File("application2.json"));

        // DELETE application
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              "");

        setDeploymentMaintainedInfo(controllerTester);
        // GET tenant application deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", GET)
                                      .userIdentity(USER_ID),
                              new File("application.json"));
        // GET an application deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default", GET)
                                      .userIdentity(USER_ID),
                              new File("deployment.json"));

        addIssues(controllerTester, ApplicationId.from("tenant1", "application1", "default"));
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
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/", GET)
                                      .userIdentity(USER_ID)
                                      .recursive("true"),
                              new File("application1-recursive.json"));

        // GET nodes
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/nodes", GET)
                             .userIdentity(USER_ID),
                              new File("application-nodes.json"));

        // GET logs
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application1/environment/prod/region/us-central-1/instance/default/logs?from=1233&to=3214", GET)
                                        .userIdentity(USER_ID),
                                        new File("logs.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application1/environment/dev/region/us-central-1/instance/default/logs?from=1233&to=3214&streaming", GET)
                        .userIdentity(USER_ID),
                "INFO - All good");

        // DELETE (cancel) ongoing change
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", DELETE)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"message\":\"Changed deployment from 'application change to 1.0.42-commit1' to 'no change' for application 'tenant1.application1'\"}");

        // DELETE (cancel) again is a no-op
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"cancel\":\"all\"}"),
                              "{\"message\":\"No deployment in progress for application 'tenant1.application1' at this time\"}");

        // POST pinning to a given version to an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying/pin", POST)
                                      .userIdentity(USER_ID)
                                      .data("6.1.0"),
                              "{\"message\":\"Triggered pin to 6.1 for tenant1.application1\"}");
        assertTrue("Action is logged to audit log",
                   tester.controller().auditLogger().readLog().entries().stream()
                         .anyMatch(entry -> entry.resource().equals("/application/v4/tenant/tenant1/application/application1/deploying/pin")));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying/pin", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");

        // DELETE only the pin to a given version
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying/pin", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to 6.1' to 'upgrade to 6.1' for application 'tenant1.application1'\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":false}");

        // POST pinning again
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying/pin", POST)
                                      .userIdentity(USER_ID)
                                      .data("6.1"),
                              "{\"message\":\"Triggered pin to 6.1 for tenant1.application1\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"platform\":\"6.1\",\"pinned\":true}");

        // DELETE only the version, but leave the pin
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying/platform", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to 6.1' to 'pin to current platform' for application 'tenant1.application1'\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", GET)
                                      .userIdentity(USER_ID), "{\"pinned\":true}");

        // DELETE also the pin to a given version
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying/pin", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Changed deployment from 'pin to current platform' to 'no change' for application 'tenant1.application1'\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", GET)
                                      .userIdentity(USER_ID), "{}");

        // POST a pause to a production job
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default/job/production-us-west-1/pause", POST)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"production-us-west-1 for tenant1.application1 paused for " + DeploymentTrigger.maxPause + "\"}");

        // POST a triggering to the same production job
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default/job/production-us-west-1", POST)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Triggered production-us-west-1 for tenant1.application1\"}");

        // POST a 'restart application' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/restart", POST)
                                      .userIdentity(USER_ID),
                              "Requested restart of tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default");

        // POST a 'restart application' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/restart", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Requested restart of tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default");

        // POST a 'restart application' in staging environment command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-central-1/instance/default/restart", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Requested restart of tenant/tenant1/application/application1/environment/staging/region/us-central-1/instance/default");

        // POST a 'restart application' in staging test command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-central-1/instance/default/restart", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Requested restart of tenant/tenant1/application/application1/environment/test/region/us-central-1/instance/default");

        // POST a 'restart application' in staging dev command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-central-1/instance/default/restart", POST)
                                      .userIdentity(USER_ID),
                              "Requested restart of tenant/tenant1/application/application1/environment/dev/region/us-central-1/instance/default");

        // POST a 'restart application' command with a host filter (other filters not supported yet)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/restart?hostname=host1", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"error-code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"No node with the hostname host1 is known.\"}", 500);

        // GET suspended
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/suspended", GET)
                                      .userIdentity(USER_ID),
                              new File("suspended.json"));

        // GET services
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/service", GET)
                                      .userIdentity(USER_ID),
                              new File("services.json"));
        // GET service
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/service/storagenode-awe3slno6mmq2fye191y324jl/state/v1/", GET)
                                      .userIdentity(USER_ID),
                              new File("service.json"));

        // DELETE application with active deployments fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("delete-with-active-deployments.json"), 400);

        // DELETE (deactivate) a deployment - dev
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default", DELETE)
                                      .userIdentity(USER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default");

        // DELETE (deactivate) a deployment - prod
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default");


        // DELETE (deactivate) a deployment is idempotent
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default");

        // POST an application package and a test jar, submitting a new application for internal pipeline deployment.
        // First attempt does not have an Athenz service definition in deployment spec, and is accepted.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(applicationPackage)),
                              "{\"message\":\"Application package version: 1.0.43-d00d, source revision of repository 'repo', branch 'master' with commit 'd00d', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        // Second attempt has a service under a different domain than the tenant of the application, and fails.
        ApplicationPackage packageWithServiceForWrongDomain = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from(ATHENZ_TENANT_DOMAIN_2.getName()), AthenzService.from("service"))
                .region("us-west-1")
                .build();
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(packageWithServiceForWrongDomain)),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz domain in deployment.xml: [domain2] must match tenant domain: [domain1]\"}", 400);

        // Third attempt finally has a service under the domain of the tenant, and succeeds.
        ApplicationPackage packageWithService = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from(ATHENZ_TENANT_DOMAIN.getName()), AthenzService.from("service"))
                .region("us-west-1")
                .build();
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(createApplicationSubmissionData(packageWithService)),
                              "{\"message\":\"Application package version: 1.0.44-d00d, source revision of repository 'repo', branch 'master' with commit 'd00d', by a@b, built against 6.1 at 1970-01-01T00:00:01Z\"}");

        ApplicationId app1 = ApplicationId.from("tenant1", "application1", "default");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(asJson(DeploymentJobs.JobReport.ofComponent(app1,
                                                                                        1234,
                                                                                        123,
                                                                                        Optional.empty(),
                                                                                        BuildJob.defaultSourceRevision))),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"" + app1 + " is set up to be deployed from internally," +
                              " and no longer accepts submissions from Screwdriver v3 jobs. If you need to revert " +
                              "to the old pipeline, please file a ticket at yo/vespa-support and request this.\"}",
                              400);

        // GET deployment job overview, after triggering system and staging test jobs.
        assertEquals(2, tester.controller().applications().deploymentTrigger().triggerReadyJobs());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default/job", GET)
                                      .userIdentity(USER_ID),
                              new File("jobs.json"));

        // GET system test job overview.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default/job/system-test", GET)
                                      .userIdentity(USER_ID),
                              new File("system-test-job.json"));

        // GET system test run 1 details.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default/job/system-test/run/1", GET)
                                      .userIdentity(USER_ID),
                              new File("system-test-details.json"));

        // DELETE a running job to have it aborted.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/instance/default/job/staging-test", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"message\":\"Aborting run 1 of stagingTest for tenant1.application1\"}");

        // DELETE submission to unsubscribe from continuous deployment.
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/submit", DELETE)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              "{\"message\":\"Unregistered 'tenant1.application1' from internal deployment pipeline.\"}");

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID)
                                      .data(asJson(DeploymentJobs.JobReport.ofComponent(app1,
                                                                                        1234,
                                                                                        123,
                                                                                        Optional.empty(),
                                                                                        BuildJob.defaultSourceRevision))),
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

        // Promote from pipeline
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/promote", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Successfully copied environment hosted-verified-prod to hosted-instance_tenant1_application1_placeholder_component_default\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/promote", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Successfully copied environment hosted-instance_tenant1_application1_placeholder_component_default to hosted-instance_tenant1_application1_us-west-1_prod_default\"}");

        // DELETE an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE).userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              "");
        // DELETE a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE).userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("tenant-without-applications.json"));
    }

    private void addIssues(ContainerControllerTester tester, ApplicationId id) {
        tester.controller().applications().lockOrThrow(id, application ->
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
                .globalServiceId("foo")
                .region("us-west-1")
                .region("us-east-3")
                .build();

        // Create tenant and deploy
        ApplicationId id = createTenantAndApplication();
        long projectId = 1;
        HttpEntity deployData = createApplicationDeployData(Optional.empty(), false);
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 100);

        // us-west-1
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/deploy", POST)
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
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"application 'tenant1.application1' has no deployment in zone prod.us-east-3 in default\"}",
                              404);

        // Change status of non-existing deployment fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/global-rotation/override", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"application 'tenant1.application1' has no deployment in zone prod.us-east-3 in default\"}",
                              404);

        // GET global rotation status
        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "us-west-1"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              new File("global-rotation.json"));

        // GET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation/override", GET)
                                      .userIdentity(USER_ID),
                              new File("global-rotation-get.json"));

        // SET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation/override", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              new File("global-rotation-put.json"));

        // DELETE global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation/override", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"unit-test\"}"),
                              new File("global-rotation-delete.json"));
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
                                      .oktaAccessToken(OKTA_AT),
                              new File("tenant-without-applications.json"));

        // Create application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("application-reference.json"));

        // Grant deploy access
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId("application1"));

        // POST (deploy) an application to a prod zone - allowed when project ID is not specified
        HttpEntity entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-central-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));

        // POST (deploy) a system application with an application package
        HttpEntity noAppEntity = createApplicationDeployData(Optional.empty(), true);
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
        tester.assertResponse(request("/application/v4/tenant/hosted-vespa/application/proxy-host/environment/prod/region/us-central-1/instance/default/deploy", POST)
                        .data(noAppEntity)
                        .userIdentity(HOSTED_VESPA_OPERATOR),
                new File("deploy-no-deployment.json"), 400);
    }

    @Test
    public void testSortsDeploymentsAndJobs() {
        tester.computeVersionStatus();

        // Deploy
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .build();
        ApplicationId id = createTenantAndApplication();
        long projectId = 1;
        HttpEntity deployData = createApplicationDeployData(Optional.empty(), false);
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 100);

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsEast3)
                        .application(id)
                        .projectId(projectId)
                        .submit();

        // New zone is added before us-east-3
        applicationPackage = new ApplicationPackageBuilder()
                .globalServiceId("foo")
                // These decides the ordering of deploymentJobs and instances in the response
                .region("us-west-1")
                .region("us-east-3")
                .build();
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 101);

        // us-west-1
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsWest1)
                        .application(id)
                        .projectId(projectId)
                        .submit();

        setZoneInRotation("rotation-fqdn-1", ZoneId.from("prod", "us-west-1"));

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(JobType.productionUsEast3)
                        .application(id)
                        .projectId(projectId)
                        .submit();

        setDeploymentMaintainedInfo(controllerTester);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", GET)
                                      .userIdentity(USER_ID),
                              new File("application-without-change-multiple-deployments.json"));
    }
    
    @Test
    public void testErrorResponses() throws Exception {
        tester.computeVersionStatus();
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        // PUT (update) non-existing tenant returns 403 as tenant access cannot be determined when the tenant does not exist
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT)
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
                                      .oktaAccessToken(OKTA_AT),
                              new File("tenant-without-applications.json"));

        // POST (add) another tenant under the same domain
        tester.assertResponse(request("/application/v4/tenant/tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not create tenant 'tenant2': The Athens domain 'domain1' is already connected to tenant 'tenant1'\"}",
                              400);

        // Add the same tenant again
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Tenant 'tenant1' already exists\"}",
                              400);

        // POST (add) an Athenz tenant with underscore in name
        tester.assertResponse(request("/application/v4/tenant/my_tenant_2", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"New tenant or application names must start with a letter, may contain no more than 20 characters, and may only contain lowercase letters, digits or dashes, but no double-dashes.\"}",
                              400);

        // POST (add) an Athenz tenant with by- prefix
        tester.assertResponse(request("/application/v4/tenant/by-tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz tenant name cannot have prefix 'by-'\"}",
                              400);

        // POST (add) an Athenz tenant with a reserved name
        tester.assertResponse(request("/application/v4/tenant/hosted-vespa", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Tenant 'hosted-vespa' already exists\"}",
                              400);

        // POST (create) an (empty) application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("application-reference.json"));

        // Create the same application again
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .oktaAccessToken(OKTA_AT)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not create 'tenant1.application1': Application already exists\"}",
                              400);

        ConfigServerMock configServer = (ConfigServerMock) container.components().getComponent(ConfigServerMock.class.getName());
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE, null));
        
        // POST (deploy) an application with an invalid application package
        HttpEntity entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-failure.json"), 400);

        // POST (deploy) an application without available capacity
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", ConfigServerException.ErrorCode.OUT_OF_CAPACITY, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-out-of-capacity.json"), 400);

        // POST (deploy) an application where activation fails
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to activate application", ConfigServerException.ErrorCode.ACTIVATION_CONFLICT, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-activation-conflict.json"), 409);

        // POST (deploy) an application where we get an internal server error
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Internal server error", ConfigServerException.ErrorCode.INTERNAL_SERVER_ERROR, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              new File("deploy-internal-server-error.json"), 500);

        // DELETE tenant which has an application
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not delete tenant 'tenant1': This tenant has active applications\"}",
                              400);

        // DELETE application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              "");
        // DELETE application again - should produce 404
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .oktaAccessToken(OKTA_AT)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not delete application 'tenant1.application1': Application not found\"}",
                              404);

        // DELETE tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("tenant-without-applications.json"));
        // DELETE tenant again returns 403 as tenant access cannot be determined when the tenant does not exist
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // Promote application chef env for nonexistent tenant/application
        tester.assertResponse(request("/application/v4/tenant/dontexist/application/dontexist/environment/prod/region/us-west-1/instance/default/promote", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // Create legancy tenant name containing underscores
        tester.controller().curator().writeTenant(new AthenzTenant(TenantName.from("my_tenant"), ATHENZ_TENANT_DOMAIN,
                                                                   new Property("property1"), Optional.empty(), Optional.empty()));
        // POST (add) a Athenz tenant with dashes duplicates existing one with underscores
        tester.assertResponse(request("/application/v4/tenant/my-tenant", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .oktaAccessToken(OKTA_AT),
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
                                      .oktaAccessToken(OKTA_AT)
                                      .userIdentity(unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"The user 'user.othertenant' is not admin in Athenz domain 'domain1'\"}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT),
                              new File("tenant-without-applications.json"),
                              200);

        // Creating an application for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(unauthorizedUser)
                                      .oktaAccessToken(OKTA_AT),
                              "{\n  \"code\" : 403,\n  \"message\" : \"Access denied\"\n}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT),
                              new File("application-reference.json"),
                              200);

        // Deploy to an authorized zone by a user tenant is disallowed
        HttpEntity entity = createApplicationDeployData(applicationPackage, true);
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

        // (Deleting it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(authorizedUser)
                                      .oktaAccessToken(OKTA_AT),
                              "",
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
                                      .oktaAccessToken(OKTA_AT),
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
                .athenzIdentity(com.yahoo.config.provision.AthenzDomain.from("invalid.domain"), com.yahoo.config.provision.AthenzService.from("service"))
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
        long screwdriverProjectId = 123;
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        Application application = controllerTester.createApplication(ATHENZ_TENANT_DOMAIN.getName(), "tenant1", "application1");
        ScrewdriverId screwdriverId = new ScrewdriverId(Long.toString(screwdriverProjectId));
        controllerTester.authorize(ATHENZ_TENANT_DOMAIN, screwdriverId, ApplicationAction.deploy, application);

        controllerTester.jobCompletion(JobType.component)
                        .application(application.id())
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackage)
                        .submit();
                              
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, false))
                                      .screwdriverIdentity(screwdriverId),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Athenz domain in deployment.xml: [invalid.domain] must match tenant domain: [domain1]\"}",
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

        Application application = controllerTester.createApplication(ATHENZ_TENANT_DOMAIN.getName(), "tenant1", "application1");
        controllerTester.authorize(ATHENZ_TENANT_DOMAIN, screwdriverId, ApplicationAction.deploy, application);

        // Allow systemtest to succeed by notifying completion of system test
        controllerTester.jobCompletion(JobType.component)
                        .application(application.id())
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
        HttpEntity entity = createApplicationDeployData(applicationPackage, true);
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
        HttpEntity entity = createApplicationDeployData(applicationPackage, true);
        tester.assertResponse(request("/application/v4/tenant/by-new-user/application/application1/environment/dev/region/us-west-1/instance/default", POST)
                                      .data(entity)
                                      .userIdentity(tenantAdmin),
                              new File("deploy-result.json"));
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

        Application application = controllerTester.createApplication(ATHENZ_TENANT_DOMAIN.getName(), "tenant1", "application1");
        controllerTester.authorize(ATHENZ_TENANT_DOMAIN, screwdriverId, ApplicationAction.deploy, application);

        // Allow systemtest to succeed by notifying completion of system test
        controllerTester.jobCompletion(JobType.component)
                .application(application.id())
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

        BuildJob job = new BuildJob(report -> notifyCompletion(report, controllerTester), controllerTester.artifactRepository())
                .application(app)
                .projectId(projectId);
        job.type(JobType.component).uploadArtifact(applicationPackage).submit();
        controllerTester.deploy(app, applicationPackage, TEST_ZONE);
        job.type(JobType.systemTest).submit();

        // Notifying about job started not by the controller fails
        Request request = request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                .data(asJson(job.type(JobType.systemTest).report()))
                .userIdentity(HOSTED_VESPA_OPERATOR)
                .get();
        tester.assertResponse(request, new File("jobreport-unexpected-system-test-completion.json"), 400);

        // Notifying about unknown job fails
        request = request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                .data(asJson(job.type(JobType.productionUsEast3).report()))
                .userIdentity(HOSTED_VESPA_OPERATOR)
                .get();
        tester.assertResponse(request, new File("jobreport-unexpected-completion.json"), 400);

        // ... and assert it was recorded
        JobStatus recordedStatus =
                tester.controller().applications().get(app.id()).get().deploymentJobs().jobStatus().get(JobType.component);

        assertNotNull("Status was recorded", recordedStatus);
        assertTrue(recordedStatus.isSuccess());
        assertEquals(vespaVersion, recordedStatus.lastCompleted().get().platform());

        recordedStatus =
                tester.controller().applications().get(app.id()).get().deploymentJobs().jobStatus().get(JobType.productionApNortheast2);
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
        BuildJob job = new BuildJob(report -> notifyCompletion(report, controllerTester), controllerTester.artifactRepository())
                .application(app)
                .projectId(projectId);
        job.type(JobType.component).uploadArtifact(applicationPackage).submit();

        controllerTester.deploy(app, applicationPackage, TEST_ZONE);
        job.type(JobType.systemTest).submit();
        controllerTester.deploy(app, applicationPackage, STAGING_ZONE);
        job.type(JobType.stagingTest).error(DeploymentJobs.JobError.outOfCapacity).submit();

        // Appropriate error is recorded
        JobStatus jobStatus = tester.controller().applications().get(app.id())
                .get()
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
        RoutingPolicy policy = new RoutingPolicy(app.id(),
                                                 ClusterSpec.Id.from("default"),
                                                 ZoneId.from(Environment.prod, RegionName.from("us-west-1")),
                                                 HostName.from("lb-0-canonical-name"),
                                                 Optional.of("dns-zone-1"), Set.of(RotationName.from("c0")));
        tester.controller().curator().writeRoutingPolicies(app.id(), Set.of(policy));

        // GET application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", GET)
                                      .userIdentity(USER_ID),
                              new File("application-with-routing-policy.json"));

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

    private HttpEntity createApplicationDeployData(ApplicationPackage applicationPackage, boolean deployDirectly) {
        return createApplicationDeployData(Optional.of(applicationPackage), deployDirectly);
    }

    private HttpEntity createApplicationDeployData(Optional<ApplicationPackage> applicationPackage, boolean deployDirectly) {
        return createApplicationDeployData(applicationPackage, Optional.empty(), deployDirectly);
    }

    private HttpEntity createApplicationDeployData(Optional<ApplicationPackage> applicationPackage,
                                                   Optional<ApplicationVersion> applicationVersion, boolean deployDirectly) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("deployOptions", deployOptions(deployDirectly, applicationVersion), ContentType.APPLICATION_JSON);
        applicationPackage.ifPresent(ap -> builder.addBinaryBody("applicationZip", ap.zippedContent()));
        return builder.build();
    }

    private HttpEntity createApplicationSubmissionData(ApplicationPackage applicationPackage) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody(EnvironmentResource.SUBMIT_OPTIONS,
                            "{\"repository\":\"repo\",\"branch\":\"master\",\"commit\":\"d00d\",\"authorEmail\":\"a@b\"}",
                            ContentType.APPLICATION_JSON);
        builder.addBinaryBody(EnvironmentResource.APPLICATION_ZIP, applicationPackage.zippedContent());
        builder.addBinaryBody(EnvironmentResource.APPLICATION_TEST_ZIP, "content".getBytes());
        return builder.build();
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
        AthenzDbMock.Domain domainMock = new AthenzDbMock.Domain(domain);
        domainMock.markAsVespaTenant();
        domainMock.admin(AthenzUser.fromUserId(userId.id()));
        mock.getSetup().addDomain(domainMock);
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
                                      .oktaAccessToken(OKTA_AT),
                              new File("tenant-without-applications.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .oktaAccessToken(OKTA_AT),
                              new File("application-reference.json"));
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID, ATHENZ_TENANT_DOMAIN,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId("application1"));

        return ApplicationId.from("tenant1", "application1", "default");
    }

    private void startAndTestChange(ContainerControllerTester controllerTester, ApplicationId application,
                                    long projectId, ApplicationPackage applicationPackage,
                                    HttpEntity deployData, long buildNumber) {
        ContainerTester tester = controllerTester.containerTester();

        // Trigger application change
        controllerTester.artifactRepository().put(application, applicationPackage,"1.0." + buildNumber
                                                                                  + "-commit1");
        controllerTester.jobCompletion(JobType.component)
                        .application(application)
                        .projectId(projectId)
                        .buildNumber(buildNumber)
                        .submit();

        // system-test
        String testPath = String.format("/application/v4/tenant/%s/application/%s/environment/test/region/us-east-1/instance/default",
                application.tenant().value(), application.application().value());
        tester.assertResponse(request(testPath, POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request(testPath, DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                "Deactivated " + testPath.replaceFirst("/application/v4/", ""));
        controllerTester.jobCompletion(JobType.systemTest)
                        .application(application)
                        .projectId(projectId)
                        .submit();

        // staging
        String stagingPath = String.format("/application/v4/tenant/%s/application/%s/environment/staging/region/us-east-3/instance/default",
                application.tenant().value(), application.application().value());
        tester.assertResponse(request(stagingPath, POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request(stagingPath, DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                "Deactivated " + stagingPath.replaceFirst("/application/v4/", ""));
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
            controllerTester.controller().applications().lockOrThrow(application.id(), lockedApplication -> {
                lockedApplication = lockedApplication.with(new ApplicationMetrics(0.5, 0.7));

                for (Deployment deployment : application.deployments().values()) {
                    Map<ClusterSpec.Id, ClusterInfo> clusterInfo = new HashMap<>();
                    List<String> hostnames = new ArrayList<>();
                    hostnames.add("host1");
                    hostnames.add("host2");
                    clusterInfo.put(ClusterSpec.Id.from("cluster1"), new ClusterInfo("flavor1", 37, 2, 4, 50, ClusterSpec.Type.content, hostnames));
                    Map<ClusterSpec.Id, ClusterUtilization> clusterUtils = new HashMap<>();
                    clusterUtils.put(ClusterSpec.Id.from("cluster1"), new ClusterUtilization(0.3, 0.6, 0.4, 0.3));
                    DeploymentMetrics metrics = new DeploymentMetrics(1, 2, 3, 4, 5,
                                                                      Optional.of(Instant.ofEpochMilli(123123)), Map.of());

                    lockedApplication = lockedApplication
                            .withClusterInfo(deployment.zone(), clusterInfo)
                            .withClusterUtilization(deployment.zone(), clusterUtils)
                            .with(deployment.zone(), metrics)
                            .recordActivityAt(Instant.parse("2018-06-01T10:15:30.00Z"), deployment.zone());
                }
                controllerTester.controller().applications().store(lockedApplication);
            });
        }
    }

    private MetricsServiceMock metricsService() {
        return (MetricsServiceMock) tester.container().components().getComponent(MetricsServiceMock.class.getName());
    }

    private MockContactRetriever contactRetriever() {
        return (MockContactRetriever) tester.container().components().getComponent(MockContactRetriever.class.getName());
    }

    private void setZoneInRotation(String rotationName, ZoneId zone) {
        String vipName = "proxy." + zone.value() + ".vip.test";
        metricsService().addRotation(rotationName)
                        .setZoneIn(rotationName, vipName);
        ApplicationController applicationController = controllerTester.controller().applications();
        List<Application> applicationList = applicationController.asList();
        applicationList.forEach(application -> {
                applicationController.lockIfPresent(application.id(), locked ->
                        applicationController.store(locked.withRotationStatus(rotationStatus(application))));
        });}

    private Map<HostName, RotationStatus> rotationStatus(Application application) {
        return controllerTester.controller().applications().rotationRepository().getRotation(application)
                .map(rotation -> controllerTester.controller().metricsService().getRotationStatus(rotation.name()))
                .map(rotationStatus -> {
                    Map<HostName, RotationStatus> result = new TreeMap<>();
                    rotationStatus.forEach((hostname, status) -> result.put(hostname, RotationStatus.in));
                    return result;
                })
                .orElseGet(Collections::emptyMap);
    }

    private void updateContactInformation() {
        Contact contact = new Contact(URI.create("www.contacts.tld/1234"), URI.create("www.properties.tld/1234"), URI.create("www.issues.tld/1234"), List.of(List.of("alice"), List.of("bob")), "queue", Optional.empty());
        tester.controller().tenants().lockIfPresent(TenantName.from("tenant2"),
                                                    LockedTenant.Athenz.class,
                                                    lockedTenant -> tester.controller().tenants().store(lockedTenant.with(contact)));
    }

    private void registerContact(long propertyId) {
        PropertyId p = new PropertyId(String.valueOf(propertyId));
        contactRetriever().addContact(p, new Contact(URI.create("www.issues.tld/" + p.id()), URI.create("www.contacts.tld/" + p.id()), URI.create("www.properties.tld/" + p.id()), List.of(Collections.singletonList("alice"),
                Collections.singletonList("bob")), "queue", Optional.empty()));
    }

    private static class RequestBuilder implements Supplier<Request> {

        private final String path;
        private final Request.Method method;
        private byte[] data = new byte[0];
        private AthenzIdentity identity;
        private OktaAccessToken oktaAccessToken;
        private String contentType = "application/json";
        private String recursive;

        private RequestBuilder(String path, Request.Method method) {
            this.path = path;
            this.method = method;
        }

        private RequestBuilder data(byte[] data) { this.data = data; return this; }
        private RequestBuilder data(String data) { return data(data.getBytes(StandardCharsets.UTF_8)); }
        private RequestBuilder data(HttpEntity data) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                data.writeTo(out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return data(out.toByteArray()).contentType(data.getContentType().getValue());
        }
        private RequestBuilder userIdentity(UserId userId) { this.identity = HostedAthenzIdentities.from(userId); return this; }
        private RequestBuilder screwdriverIdentity(ScrewdriverId screwdriverId) { this.identity = HostedAthenzIdentities.from(screwdriverId); return this; }
        private RequestBuilder oktaAccessToken(OktaAccessToken oktaAccessToken) { this.oktaAccessToken = oktaAccessToken; return this; }
        private RequestBuilder contentType(String contentType) { this.contentType = contentType; return this; }
        private RequestBuilder recursive(String recursive) { this.recursive = recursive; return this; }

        @Override
        public Request get() {
            Request request = new Request("http://localhost:8080" + path +
                                          // user and domain parameters are translated to a Principal by MockAuthorizer as we do not run HTTP filters
                                          (recursive == null ? "" : "?recursive=" + recursive),
                                          data, method);
            request.getHeaders().put("Content-Type", contentType);
            if (identity != null) {
                addIdentityToRequest(request, identity);
            }
            if (oktaAccessToken != null) {
                addOktaAccessToken(request, oktaAccessToken);
            }
            return request;
        }
    }

}
