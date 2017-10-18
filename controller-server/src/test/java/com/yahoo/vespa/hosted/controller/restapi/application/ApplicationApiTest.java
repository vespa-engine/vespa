// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ConfigServerClientMock;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzUtils;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author bratseth
 * @author mpolden
 */
public class ApplicationApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";
    private static final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("corp-us-east-1")
            .build();
    private static final String athenzUserDomain = "domain1";
    private static final String athenzScrewdriverDomain = AthenzUtils.SCREWDRIVER_DOMAIN.id();


    @Test
    public void testApplicationApi() throws Exception {
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
        tester.updateSystemVersion();

        addTenantAthenzDomain(athenzUserDomain, "mytenant"); // (Necessary but not provided in this API)

        // GET API root
        tester.assertResponse(request("/application/v4/", "", Request.Method.GET),
                              new File("root.json"));
        // GET athens domains
        tester.assertResponse(request("/application/v4/athensDomain/", "", Request.Method.GET),
                              new File("athensDomain-list.json"));
        // GET OpsDB properties
        tester.assertResponse(request("/application/v4/property/", "", Request.Method.GET),
                              new File("property-list.json"));
        // GET cookie freshness
        tester.assertResponse(request("/application/v4/cookiefreshness/", "", Request.Method.GET),
                              new File("cookiefreshness.json"));
        // POST (add) a tenant without property ID
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                 "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}", 
                                       Request.Method.POST),
                              new File("tenant-without-applications.json"));
        // PUT (modify) a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.PUT),
                              new File("tenant-without-applications.json"));
        // GET the authenticated user (with associated tenants)
        tester.assertResponse(request("/application/v4/user", "", Request.Method.GET),
                              new File("user.json"));
        // GET all tenants
        tester.assertResponse(request("/application/v4/tenant/", "", Request.Method.GET),
                              new File("tenant-list.json"));
        // POST (create) an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.POST),
                              new File("application-reference.json"));
        // GET a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", "", Request.Method.GET),
                              new File("tenant-with-application.json"));
        // GET tenant applications
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/", "", Request.Method.GET),
                              new File("application-list.json"));
        // POST triggering of a full deployment to an application (if version is omitted, current system version is used)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", "6.1.0", Request.Method.POST),
                              new File("application-deployment.json"));

        // DELETE (cancel) ongoing change
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", "", Request.Method.DELETE),
                              new File("application-deployment-cancelled.json"));

        // DELETE (cancel) again is a no-op
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", "", Request.Method.DELETE),
                              new File("application-deployment-cancelled-no-op.json"));

        // POST (deploy) an application to a zone - manual user deployment
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy",
                                      entity,
                                      Request.Method.POST,
                                      athenzUserDomain, "mytenant"),
                              new File("deploy-result.json"));

        // POST (deploy) an application to a zone. This simulates calls done by our tenant pipeline.
        ApplicationId id = ApplicationId.from("tenant1", "application1", "default");
        long screwdriverProjectId = 123;

        addScrewdriverUserToDomain("screwdriveruser1", "domain1"); // (Necessary but not provided in this API)

        // Trigger deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/deploying", "6.1.0", Request.Method.POST),
                              new File("application-deployment.json"));

        // ... systemtest
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/",
                                      createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)),
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default",
                                      "",
                                      Request.Method.DELETE),
                              "Deactivated tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default");
        controllerTester.notifyJobCompletion(id, screwdriverProjectId, true, DeploymentJobs.JobType.systemTest); // Called through the separate screwdriver/v1 API

        // ... staging
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default/",
                                      createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)),
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                              new File("deploy-result.json"));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default",
                                      "",
                                      Request.Method.DELETE),
                              "Deactivated tenant/tenant1/application/application1/environment/staging/region/us-east-3/instance/default");
        controllerTester.notifyJobCompletion(id, screwdriverProjectId, true, DeploymentJobs.JobType.stagingTest);

        // ... prod zone
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/",
                                      createApplicationDeployData(applicationPackage, Optional.of(screwdriverProjectId)),
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                              new File("deploy-result.json"));
        controllerTester.notifyJobCompletion(id, screwdriverProjectId, false, DeploymentJobs.JobType.productionCorpUsEast1);

        // GET tenant screwdriver projects
        tester.assertResponse(request("/application/v4/tenant-pipeline/", "", Request.Method.GET),
                              new File("tenant-pipelines.json"));
        // GET tenant application deployments
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", "", Request.Method.GET),
                              new File("application.json"));
        // GET an application deployment
        setDeploymentMaintainedInfo(controllerTester);
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default", "", Request.Method.GET),
                              new File("deployment.json"));
        // POST a 'restart application' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/restart",
                                      "",
                                      Request.Method.POST),
                              "Requested restart of tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");
        // POST a 'restart application' command with a host filter (other filters not supported yet)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/restart?hostname=host1",
                                      "",
                                      Request.Method.POST),
                              "Requested restart of tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");
        // POST a 'log' command
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/log",
                                      "",
                                      Request.Method.POST),
                              new File("log-response.json")); // Proxied to config server, not sure about the expected return format
        // GET (wait for) convergence
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/converge", "", Request.Method.GET),
                              new File("convergence.json"));
        // GET services
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/service", "", Request.Method.GET),
                              new File("services.json"));
        // GET service
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/service/storagenode-awe3slno6mmq2fye191y324jl/state/v1/", "", Request.Method.GET),
                              new File("service.json"));
        // DELETE (deactivate) a deployment - dev
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default",
                                      "",
                                      Request.Method.DELETE),
                              "Deactivated tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default");
        // DELETE (deactivate) a deployment - prod
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default",
                                      "",
                                      Request.Method.DELETE),
                              "Deactivated tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");

        // DELETE (deactivate) a deployment is idempotent
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default",
                                      "",
                                      Request.Method.DELETE),
                              "Deactivated tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default");

        // DELETE an application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", "", Request.Method.DELETE),
                              "");
        // DELETE a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", "", Request.Method.DELETE),
                              new File("tenant-without-applications.json"));

        // PUT (create) the authenticated user
        tester.assertResponse(request("/application/v4/user?user=newuser&domain=by",
                                      new byte[0],
                                      Request.Method.PUT,
                                      athenzUserDomain, "newuser", "application/json"),
                              new File("create-user-response.json"));
        // OPTIONS return 200 OK
        tester.assertResponse(request("/application/v4/", "", Request.Method.OPTIONS),
                              "");

        // Add another Athens domain, so we can try to create more tenants
        addTenantAthenzDomain("domain2", "mytenant"); // New domain to test tenant w/property ID
        // POST (add) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2",
                                      "{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}",
                                      Request.Method.POST),
                              new File("tenant-without-applications-with-id.json"));
        // PUT (modify) a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2",
                                      "{\"athensDomain\":\"domain2\", \"property\":\"property2\", \"propertyId\":\"1234\"}",
                                      Request.Method.PUT),
                              new File("tenant-without-applications-with-id.json"));
        // GET a tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant2", "", Request.Method.GET),
                              new File("tenant-without-applications-with-id.json"));

        // Test legacy OpsDB tenants
        // POST (add) an OpsDB tenant with property ID
        tester.assertResponse(request("/application/v4/tenant/tenant3",
                                      "{\"userGroup\":\"group1\",\"property\":\"property1\",\"propertyId\":\"1234\"}",
                                      Request.Method.POST),
                              new File("opsdb-tenant-with-id-without-applications.json"));
        // PUT (modify) the OpsDB tenant to set another property
        tester.assertResponse(request("/application/v4/tenant/tenant3",
                                      "{\"userGroup\":\"group1\",\"property\":\"property2\",\"propertyId\":\"4321\"}",
                                      Request.Method.PUT),
                              new File("opsdb-tenant-with-new-id-without-applications.json"));

        // GET global rotation status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation", "", Request.Method.GET),
                              new File("global-rotation.json"));

        // GET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/global-rotation/override", "", Request.Method.GET),
                new File("global-rotation-get.json"));

        // SET global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/global-rotation/override", "{\"reason\":\"because i can\"}", Request.Method.PUT),
                new File("global-rotation-put.json"));

        // DELETE global rotation override status
        tester.assertResponse(request("/application/v4/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/global-rotation/override", "{\"reason\":\"because i can\"}", Request.Method.DELETE),
                new File("global-rotation-delete.json"));

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/promote", "", Request.Method.POST),
                "{\"message\":\"Successfully copied environment hosted-verified-prod to hosted-instance_tenant1_application1_placeholder_component_default\"}");
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/promote", "", Request.Method.POST),
                "{\"message\":\"Successfully copied environment hosted-instance_tenant1_application1_placeholder_component_default to hosted-instance_tenant1_application1_us-west-1_prod_default\"}");

        controllerTester.controller().deconstruct();
    }

    @Test
    public void testDeployDirectly() throws Exception {
        // Setup
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
        tester.updateSystemVersion();
        addTenantAthenzDomain(athenzUserDomain, "mytenant");
        addScrewdriverUserToDomain("screwdriveruser1", "domain1");

        // Create tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST),
                              new File("tenant-without-applications.json"));

        // Create application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.POST),
                              new File("application-reference.json"));

        // POST (deploy) an application to a prod zone - allowed when project ID is not specified
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/corp-us-east-1/instance/default/deploy",
                                      entity,
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                              new File("deploy-result.json"));
    }

    @Test
    public void testSortsDeploymentsAndJobs() throws Exception {
        // Setup
        ContainerControllerTester controllerTester = new ContainerControllerTester(container, responseFiles);
        ContainerTester tester = controllerTester.containerTester();
        tester.updateSystemVersion();
        addTenantAthenzDomain(athenzUserDomain, "mytenant");
        addScrewdriverUserToDomain("screwdriveruser1", "domain1");

        // Create tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST),
                              new File("tenant-without-applications.json"));

        // Create application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.POST),
                              new File("application-reference.json"));

        // Deploy
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .build();
        ApplicationId id = ApplicationId.from("tenant1", "application1", "default");
        long projectId = 1;
        HttpEntity deployData = createApplicationDeployData(applicationPackage, Optional.of(projectId));

        startAndTestChange(controllerTester, id, projectId, deployData);

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/deploy",
                                      deployData,
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                              new File("deploy-result.json"));
        controllerTester.notifyJobCompletion(id, projectId, true, DeploymentJobs.JobType.productionUsEast3);

        // New zone is added before us-east-3
        applicationPackage = new ApplicationPackageBuilder()
                // These decides the ordering of deploymentJobs and instances in the response
                .region("us-west-1")
                .region("us-east-3")
                .build();
        deployData = createApplicationDeployData(applicationPackage, Optional.of(projectId));
        startAndTestChange(controllerTester, id, projectId, deployData);

        // us-west-1
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/deploy",
                                      deployData,
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                              new File("deploy-result.json"));
        controllerTester.notifyJobCompletion(id, projectId, true, DeploymentJobs.JobType.productionUsWest1);

        // us-east-3
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east-3/instance/default/deploy",
                                      deployData,
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                              new File("deploy-result.json"));
        controllerTester.notifyJobCompletion(id, projectId, true, DeploymentJobs.JobType.productionUsEast3);

        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1", "", Request.Method.GET),
                              new File("application-without-change-multiple-deployments.json"));
    }
    
    @Test
    public void testErrorResponses() throws Exception {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        tester.updateSystemVersion();
        addTenantAthenzDomain("domain1", "mytenant");

        // PUT (update) non-existing tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.PUT),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'tenant1' does not exist\"}",
                              404);

        // GET non-existing tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "",
                                      Request.Method.GET),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Tenant 'tenant1' does not exist\"}",
                              404);

        // GET non-existing application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.GET),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"tenant1.application1 not found\"}",
                              404);

        // GET non-existing deployment
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-east/instance/default",
                                      "",
                                      Request.Method.GET),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"tenant1.application1 not found\"}",
                              404);

        // POST (add) a tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST),
                              new File("tenant-without-applications.json"));

        // POST (add) another tenant under the same domain
        tester.assertResponse(request("/application/v4/tenant/tenant2",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not create tenant 'tenant2': The Athens domain 'domain1' is already connected to tenant 'tenant1'\"}",
                              400);

        // Add the same tenant again
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Tenant 'tenant1' already exists\"}",
                              400);

        // POST (create) an (empty) application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.POST),
                              new File("application-reference.json"));

        // Create the same application again
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.POST),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"An application with id 'tenant1.application1' already exists\"}",
                              400);

        ConfigServerClientMock configServer = (ConfigServerClientMock)container.components().getComponent("com.yahoo.vespa.hosted.controller.ConfigServerClientMock");
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", ConfigServerException.ErrorCode.INVALID_APPLICATION_PACKAGE, null));
        
        // POST (deploy) an application with an invalid application package
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy",
                                      entity,
                                      Request.Method.POST,
                                      athenzUserDomain, "mytenant"),
                              new File("deploy-failure.json"), 400);

        // POST (deploy) an application without available capacity
        configServer.throwOnNextPrepare(new ConfigServerException(new URI("server-url"), "Failed to prepare application", ConfigServerException.ErrorCode.OUT_OF_CAPACITY, null));
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/dev/region/us-west-1/instance/default/deploy",
                                      entity,
                                      Request.Method.POST,
                                      athenzUserDomain, "mytenant"),
                              new File("deploy-out-of-capacity.json"), 400);

        // DELETE tenant which has an application
        tester.assertResponse(request("/application/v4/tenant/tenant1", "", Request.Method.DELETE),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not delete tenant 'tenant1': This tenant has active applications\"}",
                              400);

        // DELETE application
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.DELETE),
                              "");
        // DELETE application again - should produce 404
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.DELETE),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not delete application 'tenant1.application1': Application not found\"}", 
                              404);
        // DELETE tenant
        tester.assertResponse(request("/application/v4/tenant/tenant1", "", Request.Method.DELETE),
                              new File("tenant-without-applications.json"));
        // DELETE tenant again - should produce 404
        tester.assertResponse(request("/application/v4/tenant/tenant1", "", Request.Method.DELETE),
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not delete tenant 'tenant1': Tenant not found\"}", 
                              404);

        // Promote application chef env for nonexistent tenant/application
        tester.assertResponse(request("/application/v4/tenant/dontexist/application/dontexist/environment/prod/region/us-west-1/instance/default/promote", "", Request.Method.POST),
                "{\"error-code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Unable to promote Chef environments for application\"}",
                500);
    }
    
    @Test
    public void testAuthorization() throws Exception {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        String authorizedUser = "mytenant";
        String unauthorizedUser = "othertenant";
        
        // Mutation without an authorized user is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST,
                                      "domain1", null),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"User is not authenticated\"}",
                              403);

        // ... but read methods are allowed
        tester.assertResponse(request("/application/v4/tenant/",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.GET,
                                      "domain1", null),
                              "[]",
                              200);

        addTenantAthenzDomain("domain1", "mytenant");

        // Creating a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST,
                                      "domain1", unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"The user 'othertenant' is not admin in Athenz domain 'domain1'\"}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.POST,
                                      "domain1", authorizedUser),
                              new File("tenant-without-applications.json"),
                              200);

        // Creating an application for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.POST,
                                      "domain1", unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"User othertenant does not have write access to tenant tenant1\"}",
                              403);

        // (Create it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.POST,
                                      "domain1", authorizedUser),
                              new File("application-reference.json"),
                              200);

        // Deploy to an authorized zone by a user tenant is disallowed
        HttpEntity entity = createApplicationDeployData(applicationPackage, Optional.empty());
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1/environment/prod/region/us-west-1/instance/default/deploy",
                                      entity,
                                      Request.Method.POST,
                                      athenzUserDomain, "mytenant"),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"Principal 'mytenant' is not a screwdriver principal, and does not have deploy access to application 'tenant1.application1'\"}", 
                              403);

        // Deleting an application for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.DELETE,
                                      "domain1", unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"User othertenant does not have write access to tenant tenant1\"}",
                              403);

        // (Deleting it with the right tenant id)
        tester.assertResponse(request("/application/v4/tenant/tenant1/application/application1",
                                      "",
                                      Request.Method.DELETE,
                                      "domain1", authorizedUser),
                              "",
                              200);

        // Updating a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain1\", \"property\":\"property1\"}",
                                      Request.Method.PUT,
                                      "domain1", unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"User othertenant does not have write access to tenant tenant1\"}",
                              403);
        
        // Change Athens domain
        addTenantAthenzDomain("domain2", "mytenant");
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "{\"athensDomain\":\"domain2\", \"property\":\"property1\"}",
                                      Request.Method.PUT,
                                      "domain1", authorizedUser),
                              "{\"type\":\"ATHENS\",\"athensDomain\":\"domain2\",\"property\":\"property1\",\"applications\":[]}",
                              200);

        // Deleting a tenant for an Athens domain the user is not admin for is disallowed
        tester.assertResponse(request("/application/v4/tenant/tenant1",
                                      "",
                                      Request.Method.DELETE,
                                      "domain1", unauthorizedUser),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"User othertenant does not have write access to tenant tenant1\"}",
                              403);
    }

    private HttpEntity createApplicationDeployData(ApplicationPackage applicationPackage, Optional<Long> screwdriverJobId) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("deployOptions", deployOptions(screwdriverJobId), ContentType.APPLICATION_JSON);
        builder.addBinaryBody("applicationZip", applicationPackage.zippedContent());
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
    
    /** Make a request with (athens) user domain1.mytenant1 */
    private Request request(String path, String data, Request.Method method) {
        return request(path, data.getBytes(StandardCharsets.UTF_8), method, "domain1", "mytenant", "application/json");
    }

    private Request request(String path, String data, Request.Method method, String domain, String user) {
        return request(path, data.getBytes(StandardCharsets.UTF_8), method, domain, user, "application/json");
    }

    private Request request(String path, byte[] data, Request.Method method, String domain, String user, String contentType) {
        // user and domain parameters are translated to a Principal by MockAuthorizer as we do not run HTTP filters
        Request request = new Request("http://localhost:8080" + path + "?domain=" + domain + 
                                                                             (user !=  null ? "&user=" + user : ""), 
                                      data, method);
        request.getHeaders().put("Content-Type", contentType);
        return request;
    }

    private Request request(String path, HttpEntity data, Request.Method method, String domain, String user) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            data.writeTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return request(path, out.toByteArray(), method, domain, user, data.getContentType().getValue());
    }

    /**
     * In production this happens outside hosted Vespa, so there is no API for it and we need to reach down into the
     * mock setup to replicate the action.
     */
    private AthenzDomain addTenantAthenzDomain(String domainName, String userName) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        AthenzDomain athensDomain = new AthenzDomain(domainName);
        AthenzDbMock.Domain domain = new AthenzDbMock.Domain(athensDomain);
        domain.markAsVespaTenant();
        domain.admin(AthenzUtils.createPrincipal(new UserId(userName)));
        mock.getSetup().addDomain(domain);
        return athensDomain;
    }

    /**
     * In production this happens outside hosted Vespa, so there is no API for it and we need to reach down into the
     * mock setup to replicate the action.
     */
    private void addScrewdriverUserToDomain(String screwdriverUserId, String domainName) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) container.components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        AthenzDbMock.Domain domain = mock.getSetup().domains.get(new AthenzDomain(domainName));
        domain.admin(new AthenzPrincipal(new AthenzDomain(athenzScrewdriverDomain), new UserId(screwdriverUserId)));
    }

    private void startAndTestChange(ContainerControllerTester controllerTester, ApplicationId application, long projectId,
                                    HttpEntity deployData) throws IOException {
        ContainerTester tester = controllerTester.containerTester();

        // Trigger application change
        controllerTester.notifyJobCompletion(application, projectId, true, DeploymentJobs.JobType.component);

        // system-test
        String testPath = String.format("/application/v4/tenant/%s/application/%s/environment/test/region/us-east-1/instance/default",
                application.tenant().value(), application.application().value());
        tester.assertResponse(request(testPath,
                                      deployData,
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                new File("deploy-result.json"));
        tester.assertResponse(request(testPath,
                "",
                Request.Method.DELETE),
                "Deactivated " + testPath.replaceFirst("/application/v4/", ""));
        controllerTester.notifyJobCompletion(application, projectId, true, DeploymentJobs.JobType.systemTest);

        // staging
        String stagingPath = String.format("/application/v4/tenant/%s/application/%s/environment/staging/region/us-east-3/instance/default",
                application.tenant().value(), application.application().value());
        tester.assertResponse(request(stagingPath,
                                      deployData,
                                      Request.Method.POST,
                                      athenzScrewdriverDomain, "screwdriveruser1"),
                new File("deploy-result.json"));
        tester.assertResponse(request(stagingPath,
                "",
                Request.Method.DELETE),
                "Deactivated " + stagingPath.replaceFirst("/application/v4/", ""));
        controllerTester.notifyJobCompletion(application, projectId, true, DeploymentJobs.JobType.stagingTest);
    }

    /**
     * Cluster info, utilization and deployment metrics are maintained async by maintainers.
     *
     * This sets these values as if the maintainers has been ran.
     *
     * @param controllerTester
     */
    private void setDeploymentMaintainedInfo(ContainerControllerTester controllerTester) {
        for (Application application : controllerTester.controller().applications().asList()) {
            try (Lock lock = controllerTester.controller().applications().lock(application.id())) {
                for (Deployment deployment : application.deployments().values()) {
                    Map<ClusterSpec.Id, ClusterInfo> clusterInfo = new HashMap<>();
                    List<String> hostnames = new ArrayList<>();
                    hostnames.add("host1");
                    hostnames.add("host2");
                    clusterInfo.put(ClusterSpec.Id.from("cluster1"), new ClusterInfo("flavor1", 37, 2, 4, 50, ClusterSpec.Type.content, hostnames));
                    Map<ClusterSpec.Id, ClusterUtilization> clusterUtils = new HashMap<>();
                    clusterUtils.put(ClusterSpec.Id.from("cluster1"), new ClusterUtilization(0.3, 0.6, 0.4, 0.3));
                    deployment = deployment.withClusterInfo(clusterInfo);
                    deployment = deployment.withClusterUtils(clusterUtils);
                    deployment = deployment.withMetrics(new DeploymentMetrics(1,2,3,4,5));
                    application = application.with(deployment);
                    controllerTester.controller().applications().store(application, lock);
                }
            }
        }
    }
}
