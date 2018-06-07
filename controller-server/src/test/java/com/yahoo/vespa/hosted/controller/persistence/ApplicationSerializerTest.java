// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentActivity;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static com.yahoo.config.provision.SystemName.main;
import static com.yahoo.vespa.hosted.controller.ControllerTester.writable;
import static java.util.Optional.empty;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ApplicationSerializerTest {

    private static final ApplicationSerializer applicationSerializer = new ApplicationSerializer();
    private static final Path testData = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/persistence/testdata/");
    private static final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private static final ZoneId zone2 = ZoneId.from("prod", "us-east-3");

    @Test
    public void testSerialization() {
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml("<deployment version='1.0'>" +
                                                               "   <staging/>" +
                                                               "</deployment>");
        ValidationOverrides validationOverrides = ValidationOverrides.fromXml("<validation-overrides version='1.0'>" +
                                                                              "  <allow until='2017-06-15'>deployment-removal</allow>" +
                                                                              "</validation-overrides>");

        List<Deployment> deployments = new ArrayList<>();
        ApplicationVersion applicationVersion1 = ApplicationVersion.from(new SourceRevision("repo1", "branch1", "commit1"), 31);
        ApplicationVersion applicationVersion2 = ApplicationVersion
                .from(new SourceRevision("repo1", "branch1", "commit1"), 32);
        Instant activityAt = Instant.parse("2018-06-01T10:15:30.00Z");
        deployments.add(new Deployment(zone1, applicationVersion1, Version.fromString("1.2.3"), Instant.ofEpochMilli(3))); // One deployment without cluster info and utils
        deployments.add(new Deployment(zone2, applicationVersion2, Version.fromString("1.2.3"), Instant.ofEpochMilli(5),
                                       createClusterUtils(3, 0.2), createClusterInfo(3, 4),
                                       new DeploymentMetrics(2,3,4,5,6),
                                       DeploymentActivity.create(Optional.of(activityAt), Optional.of(activityAt))));

        OptionalLong projectId = OptionalLong.of(123L);
        List<JobStatus> statusList = new ArrayList<>();

        statusList.add(JobStatus.initial(DeploymentJobs.JobType.systemTest)
                                .withTriggering(Version.fromString("5.6.7"), ApplicationVersion.unknown, empty(), "Test", Instant.ofEpochMilli(7))
                                .withCompletion(30, empty(), Instant.ofEpochMilli(8)));
        statusList.add(JobStatus.initial(DeploymentJobs.JobType.stagingTest)
                                .withTriggering(Version.fromString("5.6.6"), ApplicationVersion.unknown, empty(), "Test 2", Instant.ofEpochMilli(5))
                                .withCompletion(11, Optional.of(JobError.unknown), Instant.ofEpochMilli(6)));
        statusList.add(JobStatus.initial(DeploymentJobs.JobType.from(main, zone1).get())
                                .withTriggering(Version.fromString("5.6.6"), ApplicationVersion.unknown, deployments.stream().findFirst(), "Test 3", Instant.ofEpochMilli(6))
                                .withCompletion(11, empty(), Instant.ofEpochMilli(7)));

        DeploymentJobs deploymentJobs = new DeploymentJobs(projectId, statusList, empty());

        Application original = new Application(ApplicationId.from("t1", "a1", "i1"),
                                               deploymentSpec,
                                               validationOverrides,
                                               deployments, deploymentJobs,
                                               Change.of(Version.fromString("6.7")),
                                               Change.of(ApplicationVersion.from(new SourceRevision("repo", "master", "deadcafe"), 42)),
                                               Optional.of(IssueId.from("1234")),
                                               new MetricsService.ApplicationMetrics(0.5, 0.9),
                                               Optional.of(new RotationId("my-rotation")));

        Application serialized = applicationSerializer.fromSlime(applicationSerializer.toSlime(original));

        assertEquals(original.id(), serialized.id());

        assertEquals(original.deploymentSpec().xmlForm(), serialized.deploymentSpec().xmlForm());
        assertEquals(original.validationOverrides().xmlForm(), serialized.validationOverrides().xmlForm());

        assertEquals(2, serialized.deployments().size());
        assertEquals(original.deployments().get(zone1).applicationVersion(), serialized.deployments().get(zone1).applicationVersion());
        assertEquals(original.deployments().get(zone2).applicationVersion(), serialized.deployments().get(zone2).applicationVersion());
        assertEquals(original.deployments().get(zone1).version(), serialized.deployments().get(zone1).version());
        assertEquals(original.deployments().get(zone2).version(), serialized.deployments().get(zone2).version());
        assertEquals(original.deployments().get(zone1).at(), serialized.deployments().get(zone1).at());
        assertEquals(original.deployments().get(zone2).at(), serialized.deployments().get(zone2).at());
        assertEquals(original.deployments().get(zone2).activity().lastQueried().get(), serialized.deployments().get(zone2).activity().lastQueried().get());
        assertEquals(original.deployments().get(zone2).activity().lastWritten().get(), serialized.deployments().get(zone2).activity().lastWritten().get());

        assertEquals(original.deploymentJobs().projectId(), serialized.deploymentJobs().projectId());
        assertEquals(original.deploymentJobs().jobStatus().size(), serialized.deploymentJobs().jobStatus().size());
        assertEquals(  original.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest),
                     serialized.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest));
        assertEquals(  original.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.stagingTest),
                     serialized.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.stagingTest));

        assertEquals(original.outstandingChange(), serialized.outstandingChange());

        assertEquals(original.ownershipIssueId(), serialized.ownershipIssueId());

        assertEquals(original.change(), serialized.change());
        assertEquals(original.rotation().get().id(), serialized.rotation().get().id());

        // Test cluster utilization
        assertEquals(0, serialized.deployments().get(zone1).clusterUtils().size());
        assertEquals(3, serialized.deployments().get(zone2).clusterUtils().size());
        assertEquals(0.4, serialized.deployments().get(zone2).clusterUtils().get(ClusterSpec.Id.from("id2")).getCpu(), 0.01);
        assertEquals(0.2, serialized.deployments().get(zone2).clusterUtils().get(ClusterSpec.Id.from("id1")).getCpu(), 0.01);
        assertEquals(0.2, serialized.deployments().get(zone2).clusterUtils().get(ClusterSpec.Id.from("id1")).getMemory(), 0.01);

        // Test cluster info
        assertEquals(3, serialized.deployments().get(zone2).clusterInfo().size());
        assertEquals(10, serialized.deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorCost());
        assertEquals(ClusterSpec.Type.content, serialized.deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getClusterType());
        assertEquals("flavor2", serialized.deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavor());
        assertEquals(4, serialized.deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getHostnames().size());
        assertEquals(2, serialized.deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorCPU(), Double.MIN_VALUE);
        assertEquals(4, serialized.deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorMem(), Double.MIN_VALUE);
        assertEquals(50, serialized.deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorDisk(), Double.MIN_VALUE);

        // Test metrics
        assertEquals(original.metrics().queryServiceQuality(), serialized.metrics().queryServiceQuality(), Double.MIN_VALUE);
        assertEquals(original.metrics().writeServiceQuality(), serialized.metrics().writeServiceQuality(), Double.MIN_VALUE);
        assertEquals(original.deployments().get(zone2).metrics().queriesPerSecond(), serialized.deployments().get(zone2).metrics().queriesPerSecond(), Double.MIN_VALUE);
        assertEquals(original.deployments().get(zone2).metrics().writesPerSecond(), serialized.deployments().get(zone2).metrics().writesPerSecond(), Double.MIN_VALUE);
        assertEquals(original.deployments().get(zone2).metrics().documentCount(), serialized.deployments().get(zone2).metrics().documentCount(), Double.MIN_VALUE);
        assertEquals(original.deployments().get(zone2).metrics().queryLatencyMillis(), serialized.deployments().get(zone2).metrics().queryLatencyMillis(), Double.MIN_VALUE);
        assertEquals(original.deployments().get(zone2).metrics().writeLatencyMillis(), serialized.deployments().get(zone2).metrics().writeLatencyMillis(), Double.MIN_VALUE);

        { // test more deployment serialization cases
            Application original2 = writable(original).withChange(Change.of(ApplicationVersion.from(new SourceRevision("repo1", "branch1", "commit1"), 42)));
            Application serialized2 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original2));
            assertEquals(original2.change(), serialized2.change());
            assertEquals(serialized2.change().application().get().source(),
                         original2.change().application().get().source());

            Application original3 = writable(original).withChange(Change.of(ApplicationVersion.from(new SourceRevision("a", "b", "c"), 42)));
            Application serialized3 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original3));
            assertEquals(original3.change(), serialized3.change());
            assertEquals(serialized3.change().application().get().source(),
                         original3.change().application().get().source());
            Application original4 = writable(original).withChange(Change.empty());
            Application serialized4 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original4));
            assertEquals(original4.change(), serialized4.change());

            Application original5 = writable(original).withChange(Change.of(ApplicationVersion.from(new SourceRevision("a", "b", "c"), 42)));
            Application serialized5 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original5));
            assertEquals(original5.change(), serialized5.change());

            Application original6 = writable(original).withOutstandingChange(Change.of(ApplicationVersion.from(new SourceRevision("a", "b", "c"), 42)));
            Application serialized6 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original6));
            assertEquals(original6.outstandingChange(), serialized6.outstandingChange());
        }
    }

    private Map<ClusterSpec.Id, ClusterInfo> createClusterInfo(int clusters, int hosts) {
        Map<ClusterSpec.Id, ClusterInfo> result = new HashMap<>();

        for (int cluster = 0; cluster < clusters; cluster++) {
            List<String> hostnames = new ArrayList<>();
            for (int host = 0; host < hosts; host++) {
                hostnames.add("hostname" + cluster*host + host);
            }

            result.put(ClusterSpec.Id.from("id" + cluster), new ClusterInfo("flavor" + cluster, 10,
                2, 4, 50, ClusterSpec.Type.content, hostnames));
        }
        return result;
    }

    private Map<ClusterSpec.Id, ClusterUtilization> createClusterUtils(int clusters, double inc) {
        Map<ClusterSpec.Id, ClusterUtilization> result = new HashMap<>();

        ClusterUtilization util = new ClusterUtilization(0,0,0,0);
        for (int cluster = 0; cluster < clusters; cluster++) {
            double agg = cluster*inc;
            result.put(ClusterSpec.Id.from("id" + cluster), new ClusterUtilization(
                    util.getMemory()+ agg,
                    util.getCpu()+ agg,
                    util.getDisk() + agg,
                    util.getDiskBusy() + agg));
        }
        return result;
    }

    @Test
    public void testCompleteApplicationDeserialization() throws Exception {
        byte[] applicationJson = Files.readAllBytes(testData.resolve("complete-application.json"));
        applicationSerializer.fromSlime(SlimeUtils.jsonToSlime(applicationJson));
        // ok if no error
    }

    private Slime applicationSlime(boolean error) {
        return applicationSlime(123, error);
    }

    private Slime applicationSlime(long projectId, boolean error) {
        return SlimeUtils.jsonToSlime(applicationJson(projectId, error).getBytes(StandardCharsets.UTF_8));
    }

    private String applicationJson(long projectId, boolean error) {
        return
                "{\n" +
                "  \"id\": \"t1:a1:i1\",\n" +
                "  \"deploymentSpecField\": \"<deployment version='1.0'/>\",\n" +
                "  \"deploymentJobs\": {\n" +
                "    \"projectId\": " + projectId + ",\n" +
                "    \"jobStatus\": [\n" +
                "      {\n" +
                "        \"jobType\": \"system-test\",\n" +
                (error ? "        \"jobError\": \"" + JobError.unknown + "\",\n" : "") +
                "        \"lastCompleted\": {\n" +
                "          \"version\": \"6.1\",\n" +
                "          \"revision\": {\n" +
                "            \"applicationPackageHash\": \"dead\",\n" +
                "            \"sourceRevision\": {\n" +
                "              \"repositoryField\": \"git@git.foo\",\n" +
                "              \"branchField\": \"origin/master\",\n" +
                "              \"commitField\": \"cafe\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"at\": 1505725189469\n" +
                "        }\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}\n";
    }

}
