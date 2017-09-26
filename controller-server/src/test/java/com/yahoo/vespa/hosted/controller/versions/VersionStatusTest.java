// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.component;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsEast3;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.stagingTest;
import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test computing of version status
 * 
 * @author bratseth
 */
public class VersionStatusTest {
    
    @Test
    public void testEmptyVersionStatus() {
        VersionStatus status = VersionStatus.empty();
        assertFalse(status.systemVersion().isPresent());
        assertTrue(status.versions().isEmpty());
    }

    @Test
    public void testSystemVersionIsControllerVersionIfConfigserversAreNewer() {
        ControllerTester tester = new ControllerTester();
        Version largerThanCurrent = new Version(Vtag.currentVersion.getMajor() + 1);
        tester.configServer().setDefaultConfigServerVersion(largerThanCurrent);
        VersionStatus versionStatus = VersionStatus.compute(tester.controller());
        assertEquals(Vtag.currentVersion, versionStatus.systemVersion().get().versionNumber());
    }

    @Test
    public void testSystemVersionIsVersionOfOldestConfigServer() throws URISyntaxException {
        ControllerTester tester = new ControllerTester();
        Version oldest = new Version(5);
        tester.configServer().configServerVersions().put(new URI("http://cfg.prod.corp-us-east-1.test"), oldest);
        VersionStatus versionStatus = VersionStatus.compute(tester.controller());
        assertEquals(oldest, versionStatus.systemVersion().get().versionNumber());
    }

    @Test
    public void testVersionStatusAfterApplicationUpdates() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("default")
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-east-3")
                .build();

        // Application versions which are older than the current version
        Version version1 = new Version("5.1");
        Version version2 = new Version("5.2");
        tester.upgradeSystem(version1);

        // Setup applications
        Application app1 = tester.createAndDeploy("app1", 11, applicationPackage);
        Application app2 = tester.createAndDeploy("app2", 22, applicationPackage);
        Application app3 = tester.createAndDeploy("app3", 33, applicationPackage);

        // version2 is released
        tester.upgradeSystem(version2);

        // - app1 is in production on version1, but then fails in system test on version2
        tester.completeUpgradeWithError(app1, version2, applicationPackage, systemTest);
        // - app2 is partially in production on version1 and partially on version2
        tester.completeUpgradeWithError(app2, version2, applicationPackage, productionUsEast3);
        // - app3 is in production on version1, but then fails in staging test on version2
        tester.completeUpgradeWithError(app3, version2, applicationPackage, stagingTest);

        VersionStatus versionStatus = VersionStatus.compute(tester.controller());
        List<VespaVersion> versions = versionStatus.versions();
        assertEquals("The version of this controller, the default config server version, plus the two versions above exist", 4, versions.size());

        VespaVersion v0 = versions.get(2);
        assertEquals(tester.configServer().getDefaultConfigServerVersion(), v0.versionNumber());
        assertEquals(0, v0.statistics().failing().size());
        assertEquals(0, v0.statistics().production().size());

        VespaVersion v1 = versions.get(0);
        assertEquals(version1, v1.versionNumber());
        assertEquals(0, v1.statistics().failing().size());
        // All applications are on v1 in at least one zone
        assertEquals(3, v1.statistics().production().size());
        assertTrue(v1.statistics().production().contains(app2.id()));
        assertTrue(v1.statistics().production().contains(app1.id()));

        VespaVersion v2 = versions.get(1);
        assertEquals(version2, v2.versionNumber());
        // All applications have failed on v2 in at least one zone
        assertEquals(3, v2.statistics().failing().size());
        assertTrue(v2.statistics().failing().contains(app1.id()));
        assertTrue(v2.statistics().failing().contains(app3.id()));
        // Only one application is on v2 in at least one zone
        assertEquals(1, v2.statistics().production().size());
        assertTrue(v2.statistics().production().contains(app2.id()));

        VespaVersion v3 = versions.get(3);
        assertEquals(Vtag.currentVersion, v3.versionNumber());
        assertEquals(0, v3.statistics().failing().size());
        assertEquals(0, v3.statistics().production().size());
    }
    
    @Test
    public void testVersionConfidence() {
        DeploymentTester tester = new DeploymentTester();

        Version version0 = new Version("5.0");
        tester.upgradeSystem(version0);

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 0, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 1, "canary");
        Application canary2 = tester.createAndDeploy("canary2", 2, "canary");
        Application default0 = tester.createAndDeploy("default0", 00, "default");
        Application default1 = tester.createAndDeploy("default1", 11, "default");
        Application default2 = tester.createAndDeploy("default2", 22, "default");
        Application default3 = tester.createAndDeploy("default3", 33, "default");
        Application default4 = tester.createAndDeploy("default4", 44, "default");
        Application default5 = tester.createAndDeploy("default5", 55, "default");
        Application default6 = tester.createAndDeploy("default6", 66, "default");
        Application default7 = tester.createAndDeploy("default7", 77, "default");
        Application default8 = tester.createAndDeploy("default8", 88, "default");
        Application default9 = tester.createAndDeploy("default9", 99, "default");
        Application conservative0 = tester.createAndDeploy("conservative1", 000, "conservative");


        // The following applications should not affect confidence calculation:

        // Application without deployment
        Application ignored0 = tester.createApplication("ignored0", "tenant1", 1000, 1000L);

        // Pull request build
        Application ignored1 = tester.controllerTester().createApplication(new TenantId("tenant1"),
                                                                           "ignored1",
                                                                           "default-pr42", 1000);

        Version version1 = new Version("5.1");
        Version version2 = new Version("5.2");
        tester.upgradeSystem(version1);

        // Canaries upgrade to new versions and fail
        tester.completeUpgrade(canary0, version1, "canary");
        tester.completeUpgradeWithError(canary1, version1, "canary", productionUsWest1);
        tester.upgradeSystem(version2);
        tester.completeUpgrade(canary2, version2, "canary");

        VersionStatus versionStatus = VersionStatus.compute(tester.controller());
        List<VespaVersion> versions = versionStatus.versions();
        
        assertEquals("One canary failed: Broken",
                     VespaVersion.Confidence.broken, confidence(versions, version1));
        assertEquals("Nothing has failed but not all canaries has deployed: Low",
                     VespaVersion.Confidence.low, confidence(versions, version2));
        assertEquals("Current version of this - no deployments: Low",
                     VespaVersion.Confidence.low, confidence(versions, Vtag.currentVersion));

        // All canaries are upgraded to version2 which raises confidence to normal and more apps upgrade
        tester.completeUpgrade(canary0, version2, "canary");
        tester.completeUpgrade(canary1, version2, "canary");
        tester.upgradeSystem(version2);
        tester.completeUpgrade(default0, version2, "default");
        tester.completeUpgrade(default1, version2, "default");
        tester.completeUpgrade(default2, version2, "default");
        tester.completeUpgrade(default3, version2, "default");
        tester.completeUpgrade(default4, version2, "default");
        tester.completeUpgrade(default5, version2, "default");
        tester.completeUpgrade(default6, version2, "default");
        tester.completeUpgrade(default7, version2, "default");

        versionStatus = VersionStatus.compute(tester.controller());
        versions = versionStatus.versions();

        assertEquals("No deployments: Low",
                     VespaVersion.Confidence.low, confidence(versions, version0));
        assertEquals("All canaries deployed + < 90% of defaults: Normal",
                     VespaVersion.Confidence.normal, confidence(versions, version2));
        assertEquals("Current version of this - no deployments: Low",
                     VespaVersion.Confidence.low, confidence(versions, Vtag.currentVersion));

        // Another default application upgrades, raising confidence to high
        tester.completeUpgrade(default8, version2, "default");

        versionStatus = VersionStatus.compute(tester.controller());
        versions = versionStatus.versions();

        assertEquals("No deployments: Low",
                     VespaVersion.Confidence.low, confidence(versions, version0));
        assertEquals("90% of defaults deployed successfully: High",
                     VespaVersion.Confidence.high, confidence(versions, version2));
        assertEquals("Current version of this - no deployments: Low",
                     VespaVersion.Confidence.low, confidence(versions, Vtag.currentVersion));

        // A new version is released, all canaries upgrade successfully, but enough "default" apps fail to mark version
        // as broken
        Version version3 = new Version("5.3");
        tester.upgradeSystem(version3);
        tester.completeUpgrade(canary0, version3, "canary");
        tester.completeUpgrade(canary1, version3, "canary");
        tester.completeUpgrade(canary2, version3, "canary");
        tester.upgradeSystem(version3);
        tester.completeUpgradeWithError(default0, version3, "default", stagingTest);
        tester.completeUpgradeWithError(default1, version3, "default", stagingTest);
        tester.completeUpgradeWithError(default2, version3, "default", stagingTest);
        tester.completeUpgradeWithError(default9, version3, "default", stagingTest);

        versionStatus = VersionStatus.compute(tester.controller());
        versions = versionStatus.versions();

        assertEquals("No deployments: Low",
                     VespaVersion.Confidence.low, confidence(versions, version0));
        assertEquals("40% of defaults failed: Broken",
                     VespaVersion.Confidence.broken, confidence(versions, version3));
        assertEquals("Current version of this - no deployments: Low",
                     VespaVersion.Confidence.low, confidence(versions, Vtag.currentVersion));
    }

    @Test
    public void testComputeIgnoresVersionWithUnknownGitMetadata() {
        ControllerTester tester = new ControllerTester();
        ApplicationController applications = tester.controller().applications();

        tester.gitHub()
                .mockAny(false)
                .knownTag(Vtag.currentVersion.toFullString(), "foo") // controller
                .knownTag("6.1.0", "bar"); // config server

        Version versionWithUnknownTag = new Version("6.1.2");

        Application app = tester.createAndDeploy("tenant1", "domain1","application1", Environment.test, 11);
        applications.notifyJobCompletion(mockReport(app, component, true));
        applications.notifyJobCompletion(mockReport(app, systemTest, true));

        List<VespaVersion> vespaVersions = VersionStatus.compute(tester.controller()).versions();

        assertEquals(2, vespaVersions.size()); // controller and config server
        assertTrue("Version referencing unknown tag is skipped", 
                   vespaVersions.stream().noneMatch(v -> v.versionNumber().equals(versionWithUnknownTag)));
    }

    private VespaVersion.Confidence confidence(List<VespaVersion> versions, Version version) {
        return versions.stream()
                .filter(v -> v.statistics().version().equals(version))
                .findFirst()
                .map(VespaVersion::confidence)
                .orElseThrow(() -> new IllegalArgumentException("Expected to find version: " + version));
    }

    private DeploymentJobs.JobReport mockReport(Application application, DeploymentJobs.JobType jobType, boolean success) {
        return new DeploymentJobs.JobReport(
                application.id(),
                jobType,
                application.deploymentJobs().projectId().get(),
                42,
                JobError.from(success),
                false
        );
    }

}
