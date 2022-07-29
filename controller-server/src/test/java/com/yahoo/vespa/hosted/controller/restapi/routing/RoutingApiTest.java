// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.routing;

import com.yahoo.application.container.handler.Request;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author mpolden
 */
public class RoutingApiTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/routing/responses/";

    private ContainerTester tester;
    private DeploymentTester deploymentTester;

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        deploymentTester = new DeploymentTester(new ControllerTester(tester));
        deploymentTester.controllerTester().upgradeSystem(Version.fromString("6.1"));
    }

    @Test
    void discovery() {
        // Deploy
        var context = deploymentTester.newDeploymentContext("t1", "a1", "default");
        var westZone = ZoneId.from("prod", "us-west-1");
        var eastZone = ZoneId.from("prod", "us-east-3");
        var applicationPackage = new ApplicationPackageBuilder()
                .region(westZone.region())
                .region(eastZone.region())
                .endpoint("default", "default", eastZone.region().value(), westZone.region().value())
                .build();
        context.submit(applicationPackage).deploy();

        // GET root
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/", "",
                Request.Method.GET),
                new File("discovery/root.json"));

        // GET tenant
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1", "",
                Request.Method.GET),
                new File("discovery/tenant.json"));

        // GET application
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1/application/a1/",
                "",
                Request.Method.GET),
                new File("discovery/application.json"));

        // GET instance
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1/application/a1/instance/default/",
                "",
                Request.Method.GET),
                new File("discovery/instance.json"));

        // GET environment
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/", "",
                Request.Method.GET),
                new File("discovery/environment.json"));
    }

    @Test
    void recursion() {
        var context1 = deploymentTester.newDeploymentContext("t1", "a1", "default");
        var westZone = ZoneId.from("prod", "us-west-1");
        var eastZone = ZoneId.from("prod", "us-east-3");
        var package1 = new ApplicationPackageBuilder()
                .region(westZone.region())
                .region(eastZone.region())
                .endpoint("default", "default", eastZone.region().value(), westZone.region().value())
                .build();
        context1.submit(package1).deploy();

        var context2 = deploymentTester.newDeploymentContext("t1", "a2", "default");
        var package2 = new ApplicationPackageBuilder()
                .region(westZone.region())
                .region(eastZone.region())
                .endpoint("default", "default", eastZone.region().value(), westZone.region().value())
                .build();
        context2.submit(package2).deploy();

        // GET tenant recursively
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1?recursive=true", "",
                Request.Method.GET),
                new File("recursion/tenant.json"));

        // GET application recursively
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1/application/a1?recursive=true", "",
                Request.Method.GET),
                new File("recursion/application.json"));

        // GET instance recursively
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1/application/a1/instance/default?recursive=true", "",
                Request.Method.GET),
                new File("recursion/application.json"));

        // GET environment recursively
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment?recursive=true", "",
                Request.Method.GET),
                new File("recursion/environment.json"));
    }

    @Test
    void exclusive_routing() {
        var context = deploymentTester.newDeploymentContext();
        // Zones support direct routing
        var westZone = ZoneId.from("prod", "us-west-1");
        var eastZone = ZoneId.from("prod", "us-east-3");
        deploymentTester.controllerTester().zoneRegistry().exclusiveRoutingIn(ZoneApiMock.from(westZone),
                ZoneApiMock.from(eastZone));
        // Deploy application
        var applicationPackage = new ApplicationPackageBuilder()
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                .region(westZone.region())
                .region(eastZone.region())
                .endpoint("default", "default", eastZone.region().value(), westZone.region().value())
                .build();
        context.submit(applicationPackage).deploy();

        // GET initial deployment status
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("policy/deployment-status-initial.json"));

        // POST sets deployment out
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.POST),
                "{\"message\":\"Set global routing status for tenant.application in prod.us-west-1 to OUT\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("policy/deployment-status-out.json"));

        // DELETE sets deployment in
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.DELETE),
                "{\"message\":\"Set global routing status for tenant.application in prod.us-west-1 to IN\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("policy/deployment-status-in.json"));

        // GET initial zone status
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("policy/zone-status-initial.json"));

        // POST sets zone out
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/environment/prod/region/us-west-1",
                "", Request.Method.POST),
                "{\"message\":\"Set global routing status for deployments in prod.us-west-1 to OUT\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("policy/zone-status-out.json"));

        // DELETE sets zone in
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/environment/prod/region/us-west-1",
                "", Request.Method.DELETE),
                "{\"message\":\"Set global routing status for deployments in prod.us-west-1 to IN\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("policy/zone-status-in.json"));

        // Endpoint is removed
        applicationPackage = new ApplicationPackageBuilder()
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                .region(westZone.region())
                .region(eastZone.region())
                .allow(ValidationId.globalEndpointChange)
                .build();
        context.submit(applicationPackage).deploy();

        // GET deployment status. Now empty as no routing policies have global endpoints
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                "{\"deployments\":[]}");
    }

    @Test
    void shared_routing() {
        // Deploy application
        var context = deploymentTester.newDeploymentContext();
        var westZone = ZoneId.from("prod", "us-west-1");
        var eastZone = ZoneId.from("prod", "us-east-3");
        var applicationPackage = new ApplicationPackageBuilder()
                .region(westZone.region())
                .region(eastZone.region())
                .endpoint("default", "default", eastZone.region().value(), westZone.region().value())
                .build();
        context.submit(applicationPackage).deploy();

        assertNotEquals(List.of(), context.instance().rotations(), "Rotation is assigned");

        // GET initial deployment status
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("rotation/deployment-status-initial.json"));

        // POST sets deployment out
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.POST),
                "{\"message\":\"Set global routing status for tenant.application in prod.us-west-1 to OUT\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("rotation/deployment-status-out.json"));

        // DELETE sets deployment in
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.DELETE),
                "{\"message\":\"Set global routing status for tenant.application in prod.us-west-1 to IN\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("rotation/deployment-status-in.json"));

        // GET initial zone status
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("rotation/zone-status-initial.json"));

        // POST sets zone out
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/environment/prod/region/us-west-1",
                "", Request.Method.POST),
                "{\"message\":\"Set global routing status for deployments in prod.us-west-1 to OUT\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("rotation/zone-status-out.json"));

        // DELETE sets zone in
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/environment/prod/region/us-west-1",
                "", Request.Method.DELETE),
                "{\"message\":\"Set global routing status for deployments in prod.us-west-1 to IN\"}");
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("rotation/zone-status-in.json"));
    }

    @Test
    void mixed_routing_multiple_zones() {
        var westZone = ZoneId.from("prod", "us-west-1");
        var eastZone = ZoneId.from("prod", "us-east-3");

        // One shared and one exclusive zone
        deploymentTester.controllerTester().zoneRegistry().setRoutingMethod(ZoneApiMock.from(westZone),
                RoutingMethod.sharedLayer4);
        deploymentTester.controllerTester().zoneRegistry().setRoutingMethod(ZoneApiMock.from(eastZone),
                RoutingMethod.exclusive);

        // Deploy application
        var context = deploymentTester.newDeploymentContext();
        var applicationPackage = new ApplicationPackageBuilder()
                .region(westZone.region())
                .region(eastZone.region())
                .athenzIdentity(AthenzDomain.from("domain"), AthenzService.from("service"))
                .endpoint("endpoint1", "default", westZone.region().value())
                .endpoint("endpoint2", "default", eastZone.region().value())
                .build();
        context.submit(applicationPackage).deploy();

        // GET status for zone using sharedLayer4 routing
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                new File("rotation/deployment-status-initial.json"));

        // GET status for zone using exclusive routing
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-east-3",
                "", Request.Method.GET),
                "{\"deployments\":[{\"routingMethod\":\"exclusive\",\"instance\":\"tenant:application:default\"," +
                        "\"environment\":\"prod\",\"region\":\"us-east-3\",\"status\":\"in\",\"agent\":\"system\",\"changedAt\":0}]}");
    }

    @Test
    void invalid_requests() {
        // GET non-existent application
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1/application/a1/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"t1.a1 not found\"}",
                400);

        // GET, DELETE non-existent deployment
        var context = deploymentTester.newDeploymentContext();
        var applicationPackage = new ApplicationPackageBuilder()
                .region("us-east-3")
                .endpoint("default", "default")
                .build();
        context.submit(applicationPackage).deploy();
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.GET),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No such deployment: tenant.application in prod.us-west-1\"}",
                400);
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/inactive/tenant/tenant/application/application/instance/default/environment/prod/region/us-west-1",
                "", Request.Method.DELETE),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No such deployment: tenant.application in prod.us-west-1\"}",
                400);

        // GET non-existent zone
        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/environment/prod/region/us-north-1",
                "", Request.Method.GET),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No such zone: prod.us-north-1\"}",
                400);
    }

    @Test
    void endpoints_list() {
        var context = deploymentTester.newDeploymentContext("t1", "a1", "default");
        var westZone = ZoneId.from("prod", "us-west-1");
        var eastZone = ZoneId.from("prod", "us-east-3");
        var applicationPackage = new ApplicationPackageBuilder()
                .region(westZone.region())
                .region(eastZone.region())
                .endpoint("default", "default", eastZone.region().value(), westZone.region().value())
                .build();
        context.submit(applicationPackage).deploy();

        tester.assertResponse(operatorRequest("http://localhost:8080/routing/v1/status/tenant/t1/application/a1/instance/default/endpoint", "", Request.Method.GET),
                new File("endpoint/endpoints.json"));
    }

}
