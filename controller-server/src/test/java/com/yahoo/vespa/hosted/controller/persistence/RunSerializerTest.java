// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.StepInfo;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
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
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunSerializerTest {

    private static final RunSerializer serializer = new RunSerializer();
    private static final Path runFile = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/persistence/testdata/run-status.json");
    private static final RunId id = new RunId(ApplicationId.from("tenant", "application", "default"),
                                               JobType.productionUsEast3,
                                               (long) 112358);
    private static final Instant start = Instant.parse("2007-12-03T10:15:30.00Z");

    @Test
    public void testSerialization() throws IOException {
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
        assertFalse(run.hasEnded());
        assertEquals(running, run.status());
        assertEquals(3, run.lastTestLogEntry());
        assertEquals(new Version(1, 2, 3), run.versions().targetPlatform());
        ApplicationVersion applicationVersion = ApplicationVersion.from(new SourceRevision("git@github.com:user/repo.git",
                                                                                           "master",
                                                                                           "f00bad"),
                                                                        123,
                                                                        "a@b",
                                                                        Version.fromString("6.3.1"),
                                                                        Instant.ofEpochMilli(100));
        assertEquals(applicationVersion, run.versions().targetApplication());
        assertEquals(applicationVersion.authorEmail(), run.versions().targetApplication().authorEmail());
        assertEquals(applicationVersion.buildTime(), run.versions().targetApplication().buildTime());
        assertEquals(applicationVersion.compileVersion(), run.versions().targetApplication().compileVersion());
        assertEquals("f00bad", run.versions().targetApplication().commit().get());
        assertEquals("https://github.com/user/repo/tree/f00bad", run.versions().targetApplication().sourceUrl().get());
        assertEquals(new Version(1, 2, 2), run.versions().sourcePlatform().get());
        assertEquals(ApplicationVersion.from(new SourceRevision("git@github.com:user/repo.git",
                                                                "master",
                                                                "badb17"),
                                             122),
                     run.versions().sourceApplication().get());
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
                 .aborted()
                 .finished(Instant.now().truncatedTo(MILLIS));
        assertEquals(aborted, run.status());
        assertTrue(run.hasEnded());

        Run phoenix = serializer.runsFromSlime(serializer.toSlime(Collections.singleton(run))).get(id);
        assertEquals(run.id(), phoenix.id());
        assertEquals(run.start(), phoenix.start());
        assertEquals(run.end(), phoenix.end());
        assertEquals(run.status(), phoenix.status());
        assertEquals(run.lastTestLogEntry(), phoenix.lastTestLogEntry());
        assertEquals(run.testerCertificate(), phoenix.testerCertificate());
        assertEquals(run.versions(), phoenix.versions());
        assertEquals(run.steps(), phoenix.steps());

        Run initial = Run.initial(id, run.versions(), run.start());
        assertEquals(initial, serializer.runFromSlime(serializer.toSlime(initial)));
    }

}
