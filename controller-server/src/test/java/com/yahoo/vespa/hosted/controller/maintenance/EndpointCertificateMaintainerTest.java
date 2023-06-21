// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMock;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentActivity;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.QuotaUsage;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.certificate.AssignedCertificate;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.SecretStoreMock;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.productionUsWest1;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.stagingTest;
import static com.yahoo.vespa.hosted.controller.deployment.DeploymentContext.systemTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author andreer
 */
public class EndpointCertificateMaintainerTest {

    private final ControllerTester tester = new ControllerTester();
    private final SecretStoreMock secretStore = (SecretStoreMock) tester.controller().secretStore();
    private final EndpointCertificateMaintainer maintainer = new EndpointCertificateMaintainer(tester.controller(), Duration.ofHours(1));
    private final CertificatePoolMaintainer certificatePoolMaintainer = new CertificatePoolMaintainer(tester.controller(), new MockMetric(), Duration.ofHours(1), new Random(4));
    private final EndpointCertificateMetadata exampleMetadata = new EndpointCertificateMetadata("keyName", "certName", 0, 0, "root-request-uuid", Optional.of("leaf-request-uuid"), List.of(), "issuer", Optional.empty(), Optional.empty(), Optional.empty());

    @Test
    void old_and_unused_cert_is_deleted() {
        tester.curator().writeAssignedCertificate(assignedCertificate(ApplicationId.defaultId(), exampleMetadata));
        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        assertTrue(tester.curator().readAssignedCertificate(ApplicationId.defaultId()).isEmpty());
    }

    @Test
    void unused_but_recently_used_cert_is_not_deleted() {
        EndpointCertificateMetadata recentlyRequestedCert = exampleMetadata.withLastRequested(tester.clock().instant().minusSeconds(3600).getEpochSecond());
        tester.curator().writeAssignedCertificate(assignedCertificate(ApplicationId.defaultId(), recentlyRequestedCert));
        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        assertEquals(Optional.of(recentlyRequestedCert), tester.curator().readAssignedCertificate(ApplicationId.defaultId()).map(AssignedCertificate::certificate));
    }

    @Test
    void refreshed_certificate_is_updated() {
        EndpointCertificateMetadata recentlyRequestedCert = exampleMetadata.withLastRequested(tester.clock().instant().minusSeconds(3600).getEpochSecond());
        tester.curator().writeAssignedCertificate(assignedCertificate(ApplicationId.defaultId(), recentlyRequestedCert));

        secretStore.setSecret(exampleMetadata.keyName(), "foo", 1);
        secretStore.setSecret(exampleMetadata.certName(), "bar", 1);

        assertEquals(0.0, maintainer.maintain(), 0.0000001);

        var updatedCert = Optional.of(recentlyRequestedCert.withLastRefreshed(tester.clock().instant().getEpochSecond()).withVersion(1));

        assertEquals(updatedCert, tester.curator().readAssignedCertificate(ApplicationId.defaultId()).map(AssignedCertificate::certificate));
    }

    @Test
    void certificate_in_use_is_not_deleted() {
        var appId = ApplicationId.from("tenant", "application", "default");

        DeploymentTester deploymentTester = new DeploymentTester(tester);

        var applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        DeploymentContext deploymentContext = deploymentTester.newDeploymentContext("tenant", "application", "default");

        deploymentContext.submit(applicationPackage).runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1);

        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        var metadata = tester.curator().readAssignedCertificate(appId).orElseThrow().certificate();
        tester.controller().serviceRegistry().endpointCertificateProvider().certificateDetails(metadata.rootRequestId()); // cert should not be deleted, the app is deployed!
    }

    @Test
    void refreshed_certificate_is_discovered_and_after_four_days_deployed() {
        var appId = ApplicationId.from("tenant", "application", "default");

        DeploymentTester deploymentTester = new DeploymentTester(tester);

        var applicationPackage = new ApplicationPackageBuilder()
                .region("us-west-1")
                .build();

        DeploymentContext deploymentContext = deploymentTester.newDeploymentContext("tenant", "application", "default");
        deploymentContext.submit(applicationPackage).runJob(systemTest).runJob(stagingTest).runJob(productionUsWest1);
        var assignedCertificate = tester.curator().readAssignedCertificate(appId).orElseThrow();

        // cert should not be deleted, the app is deployed!
        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        assertEquals(tester.curator().readAssignedCertificate(appId), Optional.of(assignedCertificate));
        tester.controller().serviceRegistry().endpointCertificateProvider().certificateDetails(assignedCertificate.certificate().rootRequestId());

        // This simulates a cert refresh performed 3 days later
        tester.clock().advance(Duration.ofDays(3));
        secretStore.setSecret(assignedCertificate.certificate().keyName(), "foo", 1);
        secretStore.setSecret(assignedCertificate.certificate().certName(), "bar", 1);
        tester.controller().serviceRegistry().endpointCertificateProvider().requestCaSignedCertificate(appId.toFullString(), assignedCertificate.certificate().requestedDnsSans(), Optional.of(assignedCertificate.certificate()), "rsa_2048", false);
        // We should now pick up the new key and cert version + uuid, but not force trigger deployment yet
        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        deploymentContext.assertNotRunning(productionUsWest1);
        var updatedMetadata = tester.curator().readAssignedCertificate(appId).orElseThrow().certificate();
        assertNotEquals(assignedCertificate.certificate().leafRequestId().orElseThrow(), updatedMetadata.leafRequestId().orElseThrow());
        assertEquals(updatedMetadata.version(), assignedCertificate.certificate().version() + 1);

        // after another 4 days, we should force trigger deployment if it hasn't already happened
        tester.clock().advance(Duration.ofDays(4).plusSeconds(1));
        deploymentContext.assertNotRunning(productionUsWest1);
        assertEquals(0.0, maintainer.maintain(), 0.0000001);
        deploymentContext.assertRunning(productionUsWest1);
    }

    @Test
    void testEligibleSorting() {
        EndpointCertificateMaintainer.EligibleJob oldestDeployment = makeDeploymentAtAge(5);
        assertEquals(
                oldestDeployment,
                Stream.of(makeDeploymentAtAge(2), oldestDeployment, makeDeploymentAtAge(4)).min(maintainer.oldestFirst).get());
    }

    @NotNull
    private EndpointCertificateMaintainer.EligibleJob makeDeploymentAtAge(int ageInDays) {
        var deployment = new Deployment(ZoneId.defaultId(), CloudAccount.empty, RevisionId.forProduction(1), Version.emptyVersion,
                Instant.now().minus(ageInDays, ChronoUnit.DAYS), DeploymentMetrics.none, DeploymentActivity.none, QuotaUsage.none, OptionalDouble.empty());
        return new EndpointCertificateMaintainer.EligibleJob(deployment, ApplicationId.defaultId(), JobType.prod("somewhere"));
    }

    @Test
    void unmaintained_cert_is_deleted() {
        EndpointCertificateMock endpointCertificateProvider = (EndpointCertificateMock) tester.controller().serviceRegistry().endpointCertificateProvider();

        ApplicationId unknown = ApplicationId.fromSerializedForm("applicationid:is:unknown");
        var metadata = endpointCertificateProvider.requestCaSignedCertificate("something", List.of("a", "b", "c"), Optional.empty(), "rsa_2048", false);// Unknown to controller!

        assertEquals(0.0, maintainer.maintain(), 0.0000001);

        assertTrue(endpointCertificateProvider.dnsNamesOf(metadata.rootRequestId()).isEmpty());
        assertTrue(endpointCertificateProvider.listCertificates().isEmpty());
    }

    @Test
    void cert_pool_is_not_deleted() {
        EndpointCertificateMock endpointCertificateProvider = (EndpointCertificateMock) tester.controller().serviceRegistry().endpointCertificateProvider();

        tester.flagSource().withIntFlag(Flags.CERT_POOL_SIZE.id(), 3);
        assertEquals(0.0, certificatePoolMaintainer.maintain(), 0.0000001);
        assertEquals(0.0, maintainer.maintain(), 0.0000001);

        assertNotEquals(List.of(), endpointCertificateProvider.listCertificates());
    }

    private static AssignedCertificate assignedCertificate(ApplicationId instance, EndpointCertificateMetadata certificate) {
        return new AssignedCertificate(TenantAndApplicationId.from(instance), Optional.of(instance.instance()), certificate);
    }

}
