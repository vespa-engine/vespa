// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ConfigServerClientMock;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MockOrganization;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yahoo.application.container.handler.Request.Method.DELETE;
import static com.yahoo.application.container.handler.Request.Method.GET;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.application.container.handler.Request.Method.PUT;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 * @author mpolden
 * @author bjorncs
 */
public class ApplicationApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";

    private static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .globalServiceId("foo")
            .region("corp-us-east-1")
            .region("us-east-3")
            .region("us-west-1")
            .build();

    private static final AthenzDomain ATHENZ_TENANT_DOMAIN = new AthenzDomain("domain1");
    private static final ScrewdriverId SCREWDRIVER_ID = new ScrewdriverId("12345");
    private static final UserId USER_ID = new UserId("myuser");
    private static final UserId HOSTED_VESPA_OPERATOR = new UserId("johnoperator");
    private static final NToken N_TOKEN = new NToken("dummy");
    private static final ZoneId TEST_ZONE = ZoneId.from(Environment.test, RegionName.from("us-east-1"));
    private static final ZoneId STAGING_ZONE = ZoneId.from(Environment.staging, RegionName.from("us-east-3"));

    @Test
    public void testApplicationApi() throws Exception {
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
        tester.updateSystemVersion();

        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID); // (Necessary but not provided in this API)

        // GET API root
        tester.assertResponse(request("/application/v4/", GET).userIdentity(USER_ID),
                              new File("root.json"));
        // GET athens domains
        tester.assertResponse(request("/application/v4/athensDomain/", GET).userIdentity(USER_ID),
                              new File("athensDomain-list.json"));
        // GET OpsDB properties
        tester.assertResponse(request("/application/v4/property/", GET).userIdentity(USER_ID),
                              new File("property-list.json"));
        // GET cookie freshness
        tester.assertResponse(request("/application/v4/cookiefreshness/", GET).userIdentity(USER_ID),
                              new File("cookiefreshness.json"));
        // POST (add) a tenant without property ID
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .nToken(N_TOKEN),
                              new File("tenant-without-applications.json"));
        // PUT (modify) a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              new File("tenant-without-applications.json"));
        // GET the authenticated user (with associated tenants)
        tester.assertResponse(request("/application/v4/user", GET).userIdentity(USER_ID),
                              new File("user.json"));
        // GET all tenants
        tester.assertResponse(request("/application/v4/tenant/", GET).userIdentity(USER_ID),
                              new File("tenant-list.json"));


        // Add another Athens domain, so we can try to create more tenants
        createAthenzDomainWithAdmin(new AthenzDomain("domain2"), USER_ID); // New domain to test tenant w/property ID
        // Add property info for that property id, as well, in the mock organization.
        addPropertyData((MockOrganization) controllerTester.controller().organization(), "1234");
        // POST (add) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}"),
                              new File("tenant-without-applications-with-id.json"));
        // PUT (modify) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", PUT)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}"),
                              new File("tenant-without-applications-with-id.json"));
        // GET a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", GET).userIdentity(USER_ID),
                              new File("tenant-without-applications-with-id.json"));

        // Test legacy OpsDB tenants
        // POST (add) an OpsDB tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant3", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"userGroup\":\"group1\",\"property\":\"property1\",\"propertyId\":\"1234\"}"),
                              new File("opsdb-tenant-with-id-without-applications.json"));
        // PUT (modify) the OpsDB tenant to set another property
        tester.assertResponse(request("/application/v4/tenant/tenant3", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"userGroup\":\"group1\",\"property\":\"property2\",\"propertyId\":\"4321\"}"),
                              new File("opsdb-tenant-with-new-id-without-applications.json"));

        // POST (create) an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              new File("application-reference.json"));
        // GET a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", GET).userIdentity(USER_ID),
                              new File("tenant-with-application.json"));

        // GET tenant applications
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/", GET).userIdentity(USER_ID),
                              new File("application-list.json"));

        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));

        // POST triggering of a full deployment to an application (if version is omitted, current system version is used)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", POST)
                                      .userIdentity(HOSTED_VESPA_OPERATOR)
                                      .data("6.1.0"),
                              new File("application-deployment.json"));

        // DELETE (cancel) ongoing change
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", DELETE)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              new File("application-deployment-cancelled.json"));

        // DELETE (cancel) again is a no-op
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", DELETE)
                                      .userIdentity(HOSTED_VESPA_OPERATOR),
                              new File("application-deployment-cancelled-no-op.json"));

        // POST (deploy) an application to a zone - manual user deployment
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
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

        // Trigger deployment from completion of component job
        controllerTester.jobCompletion(DeploymentJobs.JobType.component)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackage)
                        .submit();

        // ... systemtest
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default");
        // Called through the separate screwdriver/v1 API
        controllerTester.jobCompletion(DeploymentJobs.JobType.systemTest)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .submit();

        // ... staging
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default");
        controllerTester.jobCompletion(DeploymentJobs.JobType.stagingTest)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .submit();

        // ... prod zone
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)))
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(DeploymentJobs.JobType.productionCorpUsEast1)
                        .application(id)
                        .projectId(screwdriverProjectId)
                        .unsuccessful()
                        .submit();

        // GET tenant screwdriver projects
        tester.assertResponse(request("/application/v4/tenant-pipeline/", GET)
                                      .userIdentity(USER_ID),
                              new File("tenant-pipelines.json"));
        setDeploymentMaintainedInfo(controllerTester);
        // GET tenant application deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", GET)
                                      .userIdentity(USER_ID),
                              new File("application.json"));
        // GET an application deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default", GET)
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


        // POST a 'restart application' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/restart", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Requested restart of tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");
        // POST a 'restart application' command with a host filter (other filters not supported yet)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/restart?hostname=host1", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Requested restart of tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");
        // POST a 'log' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/log", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("log-response.json")); // Proxied to config server, not sure about the expected return format
        // GET (wait for) convergence
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/converge", GET)
                                      .userIdentity(USER_ID),
                              new File("convergence.json"));
        // GET services
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/service", GET)
                                      .userIdentity(USER_ID),
                              new File("services.json"));
        // GET service
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/service/storagenode-awe3slno6mmq2fye191y324jl/state/v1/", GET)
                                      .userIdentity(USER_ID),
                              new File("service.json"));

        // DELETE application with active deployments fails
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE).userIdentity(USER_ID),
                              new File("delete-with-active-deployments.json"), 400);

        // DELETE (deactivate) a deployment - dev
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default", DELETE)
                                      .userIdentity(USER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default");
        // DELETE (deactivate) a deployment - prod
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");

        // DELETE (deactivate) a deployment is idempotent
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default", DELETE)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "Deactivated tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");

        // PUT (create) the authenticated user
        byte[] data = new byte[0];
        tester.assertResponse(request("/application/v4/user?user=newuser&domain=by", PUT)
                                      .data(data)
                                      .userIdentity(new UserId("newuser")),
                              new File("create-user-response.json"));
        // OPTIONS return 200 OK
        tester.assertResponse(request("/application/v4/", Request.Method.OPTIONS),
                              "");

        // GET global rotation status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation", GET)
                                      .userIdentity(USER_ID),
                              new File("global-rotation.json"));

        // GET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation/override", GET)
                                      .userIdentity(USER_ID),
                              new File("global-rotation-get.json"));

        // SET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/global-rotation/override", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"because i can\"}"),
                              new File("global-rotation-put.json"));

        // DELETE global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/global-rotation/override", DELETE)
                                      .userIdentity(USER_ID)
                                      .data("{\"reason\":\"because i can\"}"),
                              new File("global-rotation-delete.json"));

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/promote", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Successfully copied environment hosted-verified-prod to hosted-instance_tenant1_application1_placeholder_component_default\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/promote", POST)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              "{\"message\":\"Successfully copied environment hosted-instance_tenant1_application1_placeholder_component_default to hosted-instance_tenant1_application1_us-west-1_prod_default\"}");

        // DELETE an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE).userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              "");
        // DELETE a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE).userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              new File("tenant-without-applications.json"));

        controllerTester.controller().deconstruct();
    }

    private void addIssues(ContainerControllerTester tester, ApplicationId id) {
        tester.controller().applications().lockOrThrow(id, application ->
                tester.controller().applications().store(application
                                                                 .withDeploymentIssueId(IssueId.from("123"))
                                                                 .withOwnershipIssueId(IssueId.from("321"))));
    }

    @Test
    public void testDeployDirectly() throws Exception {
        // Setup
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
        tester.updateSystemVersion();
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        // Create tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST).userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .nToken(N_TOKEN),
                              new File("tenant-without-applications.json"));

        // Create application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              new File("application-reference.json"));

        // Grant deploy access
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID,
                                       ATHENZ_TENANT_DOMAIN,
                                       new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId("application1"));

        // POST (deploy) an application to a prod zone - allowed when project ID is not specified
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
    }

    @Test
    public void testSortsDeploymentsAndJobs() throws Exception {
        // Setup
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
        tester.updateSystemVersion();
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        // Create tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .nToken(N_TOKEN),
                              new File("tenant-without-applications.json"));

        // Create application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              new File("application-reference.json"));

        // Give Screwdriver project deploy access
        addScrewdriverUserToDeployRole(SCREWDRIVER_ID, ATHENZ_TENANT_DOMAIN, new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId("application1"));

        // Deploy
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .build();
        ApplicationId id = ApplicationId.from("tenant1", "application1", "default");
        long projectId = 1;
        HttpEntity deployData = createApplicationDeployData(Optional.empty(), Optional.of(projectId));
        startAndTestChange(controllerTester, id, projectId, applicationPackage, deployData, 100);

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/deploy", POST)
                                      .data(deployData)
                                      .screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(DeploymentJobs.JobType.productionUsEast3)
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
        controllerTester.jobCompletion(DeploymentJobs.JobType.productionUsWest1)
                        .application(id)
                        .projectId(projectId)
                        .submit();

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/deploy", POST)
                                      .data(deployData).screwdriverIdentity(SCREWDRIVER_ID),
                              new File("deploy-result.json"));
        controllerTester.jobCompletion(DeploymentJobs.JobType.productionUsEast3)
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
        ContainerTester tester = new ContainerTester(container, responseFiles);
        tester.updateSystemVersion();
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID);

        // PUT (update) non-existing tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'tenant1' does not exist\"}",
                              404);

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
                                      .nToken(N_TOKEN),
                              new File("tenant-without-applications.json"));

        // POST (add) another tenant under the same domain
        tester.assertResponse(request("/application/v4/tenant/tenant2", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .nToken(N_TOKEN),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not create tenant 'tenant2': The Athens domain 'domain1' is already connected to tenant 'tenant1'\"}",
                              400);

        // Add the same tenant again
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .userIdentity(USER_ID)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Tenant 'tenant1' already exists\"}",
                              400);

        // POST (create) an (empty) application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              new File("application-reference.json"));

        // Create the same application again
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not create 'tenant1.application1': Application already exists\"}",
                              400);

        ConfigServerClientMock configServer = (ConfigServerClientMock)container.components().getComponent("com.yahoo.vespa.hosted.controller.ConfigServerClientMock");
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE, null));
        
        // POST (deploy) an application with an invalid application package
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
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
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not delete tenant 'tenant1': This tenant has active applications\"}",
                              400);

        // DELETE application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              "");
        // DELETE application again - should produce 404
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not delete application 'tenant1.application1': Application not found\"}",
                              404);
        // DELETE tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID)
                                      .nToken(N_TOKEN),
                              new File("tenant-without-applications.json"));
        // DELETE tenant again - should produce 404
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not delete tenant 'tenant1': Tenant not found\"}",
                              404);

        // Promote application chef env for nonexistent tenant/application
        tester.assertResponse(request("/application/v4/tenant/dontexist/application/dontexist/environment/prod/region/us-west-1/instance/default/promote", POST)
                                      .userIdentity(USER_ID),
                              "{\"error-code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Unable to promote Chef environments for application\"}",
                              500);
    }
    
    @Test
    public void testAuthorization() throws Exception {
        ContainerTester tester = new ContainerTester(container, responseFiles);
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
                                      .userIdentity(unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"The user 'user.othertenant' is not admin in Athenz domain 'domain1'\"}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .userIdentity(authorizedUser)
                                      .nToken(N_TOKEN),
                              new File("tenant-without-applications.json"),
                              200);

        // Creating an application for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(unauthorizedUser)
                                      .nToken(N_TOKEN),
                              "{\n  \"message\" : \"Tenant admin or Vespa operator role required\"\n}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", POST)
                                      .userIdentity(authorizedUser)
                                      .nToken(N_TOKEN),
                              new File("application-reference.json"),
                              200);

        // Deploy to an authorized zone by a user tenant is disallowed
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/deploy", POST)
                                      .data(entity)
                                      .userIdentity(USER_ID),
                              "{\n  \"message\" : \"'user.myuser' is not a Screwdriver identity. Only Screwdriver is allowed to deploy to this environment.\"\n}",
                              403);

        // Deleting an application for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(unauthorizedUser),
                              "{\n  \"message\" : \"Tenant admin or Vespa operator role required\"\n}",
                              403);

        // (Deleting it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", DELETE)
                                      .userIdentity(authorizedUser)
                                      .nToken(N_TOKEN),
                              "",
                              200);

        // Updating a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                                      .userIdentity(unauthorizedUser),
                              "{\n  \"message\" : \"Tenant admin or Vespa operator role required\"\n}",
                              403);
        
        // Change Athens domain
        createAthenzDomainWithAdmin(new AthenzDomain("domain2"), USER_ID);
        tester.assertResponse(request("/application/v4/tenant/tenant1", PUT)
                                      .data("{\"athensDomain\":\"domain2\", \"property\":\"property1\"}")
                                      .userIdentity(authorizedUser)
                                      .nToken(N_TOKEN),
                              "{\"tenant\":\"tenant1\",\"type\":\"ATHENS\",\"athensDomain\":\"domain2\",\"property\":\"property1\",\"applications\":[]}",
                              200);

        // Deleting a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1", DELETE)
                                      .userIdentity(unauthorizedUser),
                              "{\n  \"message\" : \"Tenant admin or Vespa operator role required\"\n}",
                              403);
    }

    @Test
    public void deployment_fails_on_illegal_domain_in_deployment_spec() throws IOException {
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
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

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)))
                                      .screwdriverIdentity(screwdriverId),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"Athenz domain in deployment.xml: [invalid.domain] must match tenant domain: [domain1]\"}",
                              403);

    }

    @Test
    public void deployment_succeeds_when_correct_domain_is_used() throws IOException {
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
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
        controllerTester.jobCompletion(DeploymentJobs.JobType.component)
                        .application(application.id())
                        .projectId(screwdriverProjectId)
                        .uploadArtifact(applicationPackage)
                        .submit();
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/", POST)
                                      .data(createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)))
                                      .screwdriverIdentity(screwdriverId),
                              new File("deploy-result.json"));

    }

    @Test
    public void testJobStatusReporting() throws Exception {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        addUserToHostedOperatorRole(HostedAthenzIdentities.from(HOSTED_VESPA_OPERATOR));
        tester.containerTester().updateSystemVersion();
        long projectId = 1;
        Application app = tester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();

        Version vespaVersion = new Version("6.1"); // system version from mock config server client

        BuildJob job = new BuildJob(this::notifyCompletion, tester.artifactRepository())
                .application(app)
                .projectId(projectId);
        job.type(DeploymentJobs.JobType.component).uploadArtifact(applicationPackage).submit();
        tester.deploy(app, applicationPackage, TEST_ZONE, projectId);
        job.type(DeploymentJobs.JobType.systemTest).submit();

        // Notifying about unknown job fails
        Request request = request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                .data(asJson(job.type(DeploymentJobs.JobType.productionUsEast3).report()))
                .userIdentity(HOSTED_VESPA_OPERATOR)
                .get();
        tester.containerTester().assertResponse(request, new File("jobreport-unexpected-completion.json"), 400);

        // ... and assert it was recorded
        JobStatus recordedStatus =
                tester.controller().applications().get(app.id()).get().deploymentJobs().jobStatus().get(DeploymentJobs.JobType.component);

        assertNotNull("Status was recorded", recordedStatus);
        assertTrue(recordedStatus.isSuccess());
        assertEquals(vespaVersion, recordedStatus.lastCompleted().get().version());

        recordedStatus =
                tester.controller().applications().get(app.id()).get().deploymentJobs().jobStatus().get(DeploymentJobs.JobType.productionApNortheast2);
        assertNull("Status of never-triggered jobs is empty", recordedStatus);

        Response response;

        response = container.handleRequest(request("/screwdriver/v1/jobsToRun", GET).get());
        assertTrue("Response contains system-test", response.getBodyAsString().contains(DeploymentJobs.JobType.systemTest.jobName()));
        assertTrue("Response contains staging-test", response.getBodyAsString().contains(DeploymentJobs.JobType.stagingTest.jobName()));
        assertEquals("Response contains only two items", 2, SlimeUtils.jsonToSlime(response.getBody()).get().entries());

        // Check that GET didn't affect the enqueued jobs.
        response = container.handleRequest(request("/screwdriver/v1/jobsToRun", GET).get());
        assertTrue("Response contains system-test", response.getBodyAsString().contains(DeploymentJobs.JobType.systemTest.jobName()));
        assertTrue("Response contains staging-test", response.getBodyAsString().contains(DeploymentJobs.JobType.stagingTest.jobName()));
        assertEquals("Response contains only two items", 2, SlimeUtils.jsonToSlime(response.getBody()).get().entries());

    }

    @Test
    public void testJobStatusReportingOutOfCapacity() {
        ContainerControllerTester tester = new ContainerControllerTester(container, responseFiles);
        tester.containerTester().updateSystemVersion();

        long projectId = 1;
        Application app = tester.createApplication();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();

        // Report job failing with out of capacity
        BuildJob job = new BuildJob(this::notifyCompletion, tester.artifactRepository())
                .application(app)
                .projectId(projectId);
        job.type(DeploymentJobs.JobType.component).uploadArtifact(applicationPackage).submit();

        tester.deploy(app, applicationPackage, TEST_ZONE, projectId);
        job.type(DeploymentJobs.JobType.systemTest).submit();
        tester.deploy(app, applicationPackage, STAGING_ZONE, projectId);
        job.type(DeploymentJobs.JobType.stagingTest).error(DeploymentJobs.JobError.outOfCapacity).submit();

        // Appropriate error is recorded
        JobStatus jobStatus = tester.controller().applications().get(app.id())
                .get()
                .deploymentJobs()
                .jobStatus()
                .get(DeploymentJobs.JobType.stagingTest);
        assertFalse(jobStatus.isSuccess());
        assertEquals(DeploymentJobs.JobError.outOfCapacity, jobStatus.jobError().get());
    }

    private void notifyCompletion(DeploymentJobs.JobReport report) {
        assertResponse(request("/application/v4/tenant/tenant1/application/application1/jobreport", POST)
                               .userIdentity(HOSTED_VESPA_OPERATOR)
                               .data(asJson(report))
                               .get(),
                       200, "{\"message\":\"ok\"}");
    }

    private static byte[] asJson(DeploymentJobs.JobReport report) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setLong("projectId", report.projectId());
        cursor.setString("jobName", report.jobType().jobName());
        cursor.setLong("buildNumber", report.buildNumber());
        report.jobError().ifPresent(jobError -> cursor.setString("jobError", jobError.name()));
        report.sourceRevision().ifPresent(sr -> {
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

    private HttpEntity createApplicationDeployData(ApplicationPackage applicationPackage, Optional<Long> screwdriverJobId) {
        return createApplicationDeployData(Optional.of(applicationPackage), screwdriverJobId);
    }

    private HttpEntity createApplicationDeployData(Optional<ApplicationPackage> applicationPackage, Optional<Long> screwdriverJobId) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("deployOptions", deployOptions(screwdriverJobId), ContentType.APPLICATION_JSON);
        applicationPackage.ifPresent(ap -> builder.addBinaryBody("applicationZip", ap.zippedContent()));
        return builder.build();
    }
    
    private String deployOptions(Optional<Long> screwdriverJobId) {
        if (screwdriverJobId.isPresent()) // deployment from screwdriver
            return "{\"vespaVersion\":null," +
                    "\"ignoreValidationErrors\":false," +
                    "\"screwdriverBuildJob\":{\"screwdriverId\":\"" + screwdriverJobId.get() + "\"," +
                                             "\"gitRevision\":{\"repository\":\"repository1\"," +
                                                              "\"branch\":\"master\"," +
                                                              "\"commit\":\"commit1\"" +
                                                              "}" +
                                             "}" +
                    "}";
        else // This is ugly and evil, but tentatively replicates the existing behavor from the client on user deployments
            return "{\"vespaVersion\":null," +
                   "\"ignoreValidationErrors\":false," +
                   "\"screwdriverBuildJob\":{\"screwdriverId\":null," +
                                            "\"gitRevision\":{\"repository\":null," +
                                                             "\"branch\":null," +
                                                             "\"commit\":null" +
                                                             "}" +
                                            "}" +
                   "}";
            
    }

    private static class RequestBuilder implements Supplier<Request> {

        private final String path;
        private final Request.Method method;
        private byte[] data = new byte[0];
        private AthenzIdentity identity;
        private NToken nToken;
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
        private RequestBuilder nToken(NToken nToken) { this.nToken = nToken; return this; }
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
            if (nToken != null) {
                addNTokenToRequest(request, nToken);
            }
            return request;
        }
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

    private void startAndTestChange(ContainerControllerTester controllerTester, ApplicationId application,
                                    long projectId, ApplicationPackage applicationPackage,
                                    HttpEntity deployData, long buildNumber) throws IOException {
        ContainerTester tester = controllerTester.containerTester();

        // Trigger application change
        controllerTester.artifactRepository().put(application, applicationPackage,"1.0." + buildNumber
                                                                                  + "-commit1");
        controllerTester.jobCompletion(DeploymentJobs.JobType.component)
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
        controllerTester.jobCompletion(DeploymentJobs.JobType.systemTest)
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
        controllerTester.jobCompletion(DeploymentJobs.JobType.stagingTest)
                        .application(application)
                        .projectId(projectId)
                        .submit();
    }

    /**
     * Cluster info, utilization and application and deployment metrics are maintained async by maintainers.
     *
     * This sets these values as if the maintainers has been ran.
     *
     * @param controllerTester
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
                    DeploymentMetrics metrics = new DeploymentMetrics(1,2,3,4,5);

                    lockedApplication = lockedApplication
                            .withClusterInfo(deployment.zone(), clusterInfo)
                            .withClusterUtilization(deployment.zone(), clusterUtils)
                            .with(deployment.zone(), metrics);
                }
                controllerTester.controller().applications().store(lockedApplication);
            });
        }
    }

    private void addPropertyData(MockOrganization organization, String propertyIdValue) {
        PropertyId propertyId = new PropertyId(propertyIdValue);
        organization.addProperty(propertyId);
        organization.setContactsFor(propertyId, Arrays.asList(Collections.singletonList(User.from("alice")),
                                                              Collections.singletonList(User.from("bob"))));
    }

}
