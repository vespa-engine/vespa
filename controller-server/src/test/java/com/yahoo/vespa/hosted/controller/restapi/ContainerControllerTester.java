// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockBuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentSteps;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.security.AthenzCredentials;
import com.yahoo.vespa.hosted.controller.security.AthenzTenantSpec;

import java.io.File;
import java.time.Duration;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static org.junit.Assert.assertFalse;

/**
 * Provides testing of controller functionality accessed through the container
 * 
 * @author bratseth
 */
public class ContainerControllerTester {

    private final ContainerTester containerTester;
    private final Upgrader upgrader;

    public ContainerControllerTester(JDisc container, String responseFilePath) {
        containerTester = new ContainerTester(container, responseFilePath);
        CuratorDb curatorDb = controller().curator();
        upgrader = new Upgrader(controller(), Duration.ofDays(1), new JobControl(curatorDb), curatorDb);
        upgrader.setUpgradesPerMinute(100); // Anything to make it more than one per maintenance interval.
    }

    public Controller controller() { return containerTester.controller(); }

    public Upgrader upgrader() { return upgrader; }

    /** Returns the wrapped generic container tester */
    public ContainerTester containerTester() { return containerTester; }

    public Application createApplication() {
        return createApplication("domain1","tenant1", "application1", "default");
    }

    public Application createApplication(String athensDomain, String tenant, String application, String instance) {
        AthenzDomain domain1 = addTenantAthenzDomain(athensDomain, "user");
        AthenzPrincipal user = new AthenzPrincipal(new AthenzUser("user"));
        AthenzCredentials credentials = new AthenzCredentials(user, domain1, new OktaAccessToken("okta-token"));
        AthenzTenantSpec tenantSpec = new AthenzTenantSpec(TenantName.from(tenant),
                                                           domain1,
                                                           new Property("property1"),
                                                           Optional.of(new PropertyId("1234")));
        controller().tenants().create(tenantSpec, credentials);

        TenantAndApplicationId id = TenantAndApplicationId.from(tenant, application);
        controller().applications().createApplication(id, Optional.of(credentials));
        controller().applications().createInstance(id.instance(instance));
        return controller().applications().requireApplication(id);
    }

    public void deploy(ApplicationId id, ApplicationPackage applicationPackage, ZoneId zone) {
        controller().applications().deploy(id, zone, Optional.of(applicationPackage),
                                           new DeployOptions(false, Optional.empty(), false, false));
    }

    public void deployCompletely(Application application, ApplicationPackage applicationPackage, long projectId,
                                 boolean failStaging) {
        jobCompletion(JobType.component).application(application)
                                        .projectId(projectId)
                                        .uploadArtifact(applicationPackage)
                                        .submit();
        DeploymentSteps steps = controller().applications().deploymentTrigger().steps(applicationPackage.deploymentSpec());
        // TODO jonmv: Connect instances from deployment spec to deployments below.
        boolean succeeding = true;
        for (var job : steps.jobs()) {
            if (!succeeding) return;
            var zone = job.zone(controller().system());
            deploy(application.id().defaultInstance(), applicationPackage, zone);
            if (failStaging && zone.environment() == Environment.staging) {
                succeeding = false;
            }
            if (zone.environment().isTest()) {
                controller().applications().deactivate(application.id().defaultInstance(), zone);
            }
            jobCompletion(job).application(application).success(succeeding).projectId(projectId).submit();
        }
    }

    /** Notify the controller about a job completing */
    public BuildJob jobCompletion(JobType job) {
        return new BuildJob(this::notifyJobCompletion, containerTester.serviceRegistry().artifactRepositoryMock()).type(job);
    }

    // ---- Delegators:
    
    public void assertResponse(Request request, File expectedResponse) {
        containerTester.assertResponse(request, expectedResponse);
    }

    public void assertResponse(Request request, String expectedResponse, int expectedStatusCode) {
        containerTester.assertResponse(() -> request, expectedResponse, expectedStatusCode);
    }

    /*
     * Authorize action on tenantDomain/application for a given screwdriverId
     */
    public void authorize(AthenzDomain tenantDomain, ScrewdriverId screwdriverId, ApplicationAction action, TenantAndApplicationId id) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) containerTester.container().components()
                .getComponent(AthenzClientFactoryMock.class.getName());

        mock.getSetup()
                .domains.get(tenantDomain)
                .applications.get(new com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId(id.application().value()))
                .addRoleMember(action, HostedAthenzIdentities.from(screwdriverId));
    }

    private void notifyJobCompletion(DeploymentJobs.JobReport report) {
        MockBuildService buildService = containerTester.serviceRegistry().buildServiceMock();
        if (report.jobType() != component && ! buildService.remove(report.buildJob()))
            throw new IllegalArgumentException(report.jobType() + " is not running for " + report.applicationId());
        assertFalse("Unexpected entry '" + report.jobType() + "@" + report.projectId() + " in: " + buildService.jobs(),
                    buildService.remove(report.buildJob()));
        controller().applications().deploymentTrigger().notifyOfCompletion(report);
        controller().applications().deploymentTrigger().triggerReadyJobs();
    }

    private AthenzDomain addTenantAthenzDomain(String domainName, String userName) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) containerTester.container().components()
                                                                                .getComponent(AthenzClientFactoryMock.class.getName());
        AthenzDomain athensDomain = new AthenzDomain(domainName);
        AthenzDbMock.Domain domain = mock.getSetup().getOrCreateDomain(athensDomain);
        domain.markAsVespaTenant();
        domain.admin(new AthenzUser(userName));
        return athensDomain;
    }

}
