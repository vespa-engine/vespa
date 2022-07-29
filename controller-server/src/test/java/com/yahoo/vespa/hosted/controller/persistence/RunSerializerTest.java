// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.deployment.ConvergenceSummary;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.JobProfile;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.StepInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.aborted;
import static com.yahoo.vespa.hosted.controller.deployment.RunStatus.running;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.failed;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;
import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.unfinished;
import static com.yahoo.vespa.hosted.controller.deployment.Step.copyVespaLogs;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deactivateTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.deployTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.endTests;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installInitialReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installReal;
import static com.yahoo.vespa.hosted.controller.deployment.Step.installTester;
import static com.yahoo.vespa.hosted.controller.deployment.Step.report;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startStagingSetup;
import static com.yahoo.vespa.hosted.controller.deployment.Step.startTests;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunSerializerTest {

    private static final RunSerializer serializer = new RunSerializer();
    private static final Path runFile = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/persistence/testdata/run-status.json");
    private static final RunId id = new RunId(ApplicationId.from("tenant", "application", "default"),
                                              DeploymentContext.productionUsEast3,
                                              112358);
    private static final Instant start = Instant.parse("2007-12-03T10:15:30.00Z");

    @Test
    void testSerialization() throws IOException {
        for (Step step : Step.values())
            assertEquals(step, RunSerializer.stepOf(RunSerializer.valueOf(step)));

        for (Step.Status status : Step.Status.values())
            assertEquals(status, RunSerializer.stepStatusOf(RunSerializer.valueOf(status)));

        for (RunStatus status : RunStatus.values())
            assertEquals(status, RunSerializer.runStatusOf(RunSerializer.valueOf(status)));

        // The purpose of this serialised data is to ensure a new format does not break everything, so keep it up to date!
        Run run = serializer.runsFromSlime(SlimeUtils.jsonToSlime(Files.readAllBytes(runFile))).get(id);
        for (Step step : Step.values())
            assertTrue(run.steps().containsKey(step));

        assertEquals(id, run.id());
        assertEquals(start, run.start());
        assertEquals(Optional.of(Instant.ofEpochMilli(321321321321L)), run.noNodesDownSince());
        assertFalse(run.hasEnded());
        assertEquals(running, run.status());
        assertEquals(3, run.lastTestLogEntry());
        assertEquals(new Version(1, 2, 3), run.versions().targetPlatform());
        RevisionId revision1 = RevisionId.forDevelopment(123, id.job());
        RevisionId revision2 = RevisionId.forProduction(122);
        assertEquals(revision1, run.versions().targetRevision());
        assertEquals("because", run.reason().get());
        assertEquals(new Version(1, 2, 2), run.versions().sourcePlatform().get());
        assertEquals(revision2, run.versions().sourceRevision().get());
        assertEquals(Optional.of(new ConvergenceSummary(1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233)),
                run.convergenceSummary());
        assertEquals(X509CertificateUtils.fromPem("-----BEGIN CERTIFICATE-----\n" +
                        "MIIBEzCBu6ADAgECAgEBMAoGCCqGSM49BAMEMBQxEjAQBgNVBAMTCW15c2Vydmlj\n" +
                        "ZTAeFw0xOTA5MDYwNzM3MDZaFw0xOTA5MDcwNzM3MDZaMBQxEjAQBgNVBAMTCW15\n" +
                        "c2VydmljZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABM0JhD8fV2DlAkjQOGX3\n" +
                        "Y50ryMBr3g2+v/uFiRoxJ1muuSOWYrW7HCQIGuzc04fa0QwtaX/voAZKCV51t6jF\n" +
                        "0fwwCgYIKoZIzj0EAwQDRwAwRAIgVbQ3Co1H4X0gmRrtXSyTU0HgBQu9PXHMmX20\n" +
                        "5MyyPSoCIBltOcmaPfdN03L3zqbqZ6PgUBWsvAHgiBzL3hrtJ+iy\n" +
                        "-----END CERTIFICATE-----"),
                run.testerCertificate().get());
        assertEquals(ImmutableMap.<Step, StepInfo>builder()
                        .put(deployInitialReal, new StepInfo(deployInitialReal, unfinished, Optional.empty()))
                        .put(installInitialReal, new StepInfo(installInitialReal, failed, Optional.of(Instant.ofEpochMilli(1196676940000L))))
                        .put(deployReal, new StepInfo(deployReal, succeeded, Optional.empty()))
                        .put(installReal, new StepInfo(installReal, unfinished, Optional.empty()))
                        .put(deactivateReal, new StepInfo(deactivateReal, failed, Optional.empty()))
                        .put(deployTester, new StepInfo(deployTester, succeeded, Optional.empty()))
                        .put(installTester, new StepInfo(installTester, unfinished, Optional.of(Instant.ofEpochMilli(1196677940000L))))
                        .put(deactivateTester, new StepInfo(deactivateTester, failed, Optional.empty()))
                        .put(copyVespaLogs, new StepInfo(copyVespaLogs, succeeded, Optional.empty()))
                        .put(startStagingSetup, new StepInfo(startStagingSetup, succeeded, Optional.empty()))
                        .put(endStagingSetup, new StepInfo(endStagingSetup, unfinished, Optional.empty()))
                        .put(startTests, new StepInfo(startTests, succeeded, Optional.empty()))
                        .put(endTests, new StepInfo(endTests, unfinished, Optional.empty()))
                        .put(report, new StepInfo(report, failed, Optional.empty()))
                        .build(),
                run.steps());

        run = run.with(1L << 50)
                .with(Instant.now().truncatedTo(MILLIS))
                .noNodesDownSince(Instant.now().truncatedTo(MILLIS))
                .aborted()
                .finished(Instant.now().truncatedTo(MILLIS));
        assertEquals(aborted, run.status());
        assertTrue(run.hasEnded());

        Run phoenix = serializer.runsFromSlime(serializer.toSlime(List.of(run))).get(id);
        assertEquals(run.id(), phoenix.id());
        assertEquals(run.start(), phoenix.start());
        assertEquals(run.end(), phoenix.end());
        assertEquals(run.status(), phoenix.status());
        assertEquals(run.lastTestLogEntry(), phoenix.lastTestLogEntry());
        assertEquals(run.lastVespaLogTimestamp(), phoenix.lastVespaLogTimestamp());
        assertEquals(run.noNodesDownSince(), phoenix.noNodesDownSince());
        assertEquals(run.testerCertificate(), phoenix.testerCertificate());
        assertEquals(run.versions(), phoenix.versions());
        assertEquals(run.steps(), phoenix.steps());
        assertEquals(run.isDryRun(), phoenix.isDryRun());
        assertEquals(run.reason(), phoenix.reason());

        assertEquals(new String(SlimeUtils.toJsonBytes(serializer.toSlime(run).get(), false), UTF_8),
                new String(SlimeUtils.toJsonBytes(serializer.toSlime(phoenix).get(), false), UTF_8));

        Run initial = Run.initial(id, run.versions(), run.isRedeployment(), run.start(), JobProfile.production, Optional.empty());
        assertEquals(initial, serializer.runFromSlime(serializer.toSlime(initial)));
    }

}
