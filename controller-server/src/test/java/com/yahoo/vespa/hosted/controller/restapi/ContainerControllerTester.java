// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TestIdentities;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.GitRevision;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ScrewdriverBuildJob;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athens.Athens;
import com.yahoo.vespa.hosted.controller.api.integration.athens.AthensPrincipal;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.api.integration.athens.mock.AthensMock;
import com.yahoo.vespa.hosted.controller.api.integration.athens.mock.AthensDbMock;
import com.yahoo.vespa.hosted.controller.api.integration.athens.mock.ZmsClientFactoryMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Provides testing of controller functionality accessed through the container
 * 
 * @author bratseth
 */
public class ContainerControllerTester {

    private final ContainerTester containerTester;
    private final Controller controller;
    private final Upgrader upgrader;

    public ContainerControllerTester(JDisc container, String responseFilePath) {
        containerTester = new ContainerTester(container, responseFilePath);
        controller = (Controller)container.components().getComponent("com.yahoo.vespa.hosted.controller.Controller");
        upgrader = new Upgrader(controller, Duration.ofMinutes(2), new JobControl(new MockCuratorDb()));
    }

    public Controller controller() { return controller; }

    public Upgrader upgrader() { return upgrader; }

    /** Returns the wrapped generic container tester */
    public ContainerTester containerTester() { return containerTester; }

    public Application createApplication() {
        return createApplication("domain1","tenant1",
                                 "application1");
    }

    public Application createApplication(String athensDomain, String tenant, String application) {
        AthensDomain domain1 = addTenantAthensDomain(athensDomain, "mytenant");
        controller.tenants().addTenant(Tenant.createAthensTenant(new TenantId(tenant), domain1,
                                                                 new Property("property1"),
                                                                 Optional.of(new PropertyId("1234"))),
                                       Optional.of(TestIdentities.userNToken));
        ApplicationId app = ApplicationId.from(tenant, application, "default");
        return controller.applications().createApplication(app, Optional.of(TestIdentities.userNToken));
    }

    public Application deploy(Application application, ApplicationPackage applicationPackage, Zone zone, long projectId) {
        ScrewdriverId app1ScrewdriverId = new ScrewdriverId(String.valueOf(projectId));
        GitRevision app1RevisionId = new GitRevision(new GitRepository("repo"), new GitBranch("master"), new GitCommit("commit1"));
        controller.applications().deployApplication(application.id(),
                                                    zone,
                                                    applicationPackage,
                                                    new DeployOptions(Optional.of(new ScrewdriverBuildJob(app1ScrewdriverId, app1RevisionId)), Optional.empty(), false, false));
        return application;
    }

    public void notifyJobCompletion(ApplicationId applicationId, long projectId, boolean success, DeploymentJobs.JobType job) {
        controller().applications().notifyJobCompletion(new DeploymentJobs.JobReport(applicationId, job, projectId,
                                                                                     42,
                                                                                     success ? Optional.empty() : Optional.of(DeploymentJobs.JobError.unknown)
        ));
    }

    public AthensDomain addTenantAthensDomain(String domainName, String userName) {
        Athens athens = (AthensMock) containerTester.container().components().getComponent(
                "com.yahoo.vespa.hosted.controller.api.integration.athens.mock.AthensMock"
        );
        ZmsClientFactoryMock mock = (ZmsClientFactoryMock) athens.zmsClientFactory();
        AthensDomain athensDomain = new AthensDomain(domainName);
        AthensDbMock.Domain domain = new AthensDbMock.Domain(athensDomain);
        domain.markAsVespaTenant();
        domain.admin(new AthensPrincipal(new AthensDomain("domain"), new UserId(userName)));
        mock.getSetup().addDomain(domain);
        return athensDomain;
    }

    // ---- Delegators:
    
    public void assertResponse(Request request, File expectedResponse) throws IOException {
        containerTester.assertResponse(request, expectedResponse);
    }

}
