// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.SourceRevision;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.AssignedRotation;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentActivity;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationState;
import com.yahoo.vespa.hosted.controller.rotation.RotationStatus;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import static com.yahoo.config.provision.SystemName.main;
import static java.util.Optional.empty;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */

public class ApplicationSerializerTest {

    private static final ApplicationSerializer APPLICATION_SERIALIZER = new ApplicationSerializer();
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

        OptionalLong projectId = OptionalLong.of(123L);

        List<Deployment> deployments = new ArrayList<>();
        ApplicationVersion applicationVersion1 = ApplicationVersion.from(new SourceRevision("repo1", "branch1", "commit1"), 31);
        ApplicationVersion applicationVersion2 = ApplicationVersion
                .from(new SourceRevision("repo1", "branch1", "commit1"), 32, "a@b",
                      Version.fromString("6.3.1"), Instant.ofEpochMilli(496));
        Instant activityAt = Instant.parse("2018-06-01T10:15:30.00Z");
        deployments.add(new Deployment(zone1, applicationVersion1, Version.fromString("1.2.3"), Instant.ofEpochMilli(3))); // One deployment without cluster info and utils
        deployments.add(new Deployment(zone2, applicationVersion2, Version.fromString("1.2.3"), Instant.ofEpochMilli(5),
                                       createClusterUtils(3, 0.2), createClusterInfo(3, 4),
                                       new DeploymentMetrics(2, 3, 4, 5, 6,
                                                             Optional.of(Instant.now().truncatedTo(ChronoUnit.MILLIS)),
                                                             Map.of(DeploymentMetrics.Warning.all, 3)),
                                       DeploymentActivity.create(Optional.of(activityAt), Optional.of(activityAt),
                                                                 OptionalDouble.of(200), OptionalDouble.of(10))));

        List<JobStatus> statusList = new ArrayList<>();

        statusList.add(JobStatus.initial(JobType.systemTest)
                                .withTriggering(Version.fromString("5.6.7"), ApplicationVersion.unknown, empty(), "Test", Instant.ofEpochMilli(7))
                                .withCompletion(30, empty(), Instant.ofEpochMilli(8))
                                .withPause(OptionalLong.of(1L << 32)));
        statusList.add(JobStatus.initial(JobType.stagingTest)
                                .withTriggering(Version.fromString("5.6.6"), ApplicationVersion.unknown, empty(), "Test 2", Instant.ofEpochMilli(5))
                                .withCompletion(11, Optional.of(JobError.unknown), Instant.ofEpochMilli(6)));
        statusList.add(JobStatus.initial(JobType.from(main, zone1).get())
                                .withTriggering(Version.fromString("5.6.6"), ApplicationVersion.unknown, deployments.stream().findFirst(), "Test 3", Instant.ofEpochMilli(6))
                                .withCompletion(11, empty(), Instant.ofEpochMilli(7)));

        DeploymentJobs deploymentJobs = new DeploymentJobs(OptionalLong.empty(), statusList, empty(), true);

        var rotationStatus = RotationStatus.from(Map.of(new RotationId("my-rotation"),
                                                        Map.of(ZoneId.from("prod", "us-west-1"), RotationState.in,
                                                               ZoneId.from("prod", "us-east-3"), RotationState.out)));

        ApplicationId id1 = ApplicationId.from("t1", "a1", "i1");
        ApplicationId id3 = ApplicationId.from("t1", "a1", "i3");
        List<Instance> instances = List.of(new Instance(id1,
                                                        deployments,
                                                        deploymentJobs,
                                                        List.of(AssignedRotation.fromStrings("foo", "default", "my-rotation", Set.of())),
                                                        rotationStatus),
                                           new Instance(id3,
                                                        List.of(),
                                                        new DeploymentJobs(OptionalLong.empty(), List.of(), empty(), true),
                                                        List.of(),
                                                        RotationStatus.EMPTY));

        Application original = new Application(TenantAndApplicationId.from(id1),
                                               Instant.now().truncatedTo(ChronoUnit.MILLIS),
                                               deploymentSpec,
                                               validationOverrides,
                                               Change.of(Version.fromString("6.7")).withPin(),
                                               Change.of(ApplicationVersion.from(new SourceRevision("repo", "master", "deadcafe"), 42)),
                                               Optional.of(IssueId.from("4321")),
                                               Optional.of(IssueId.from("1234")),
                                               Optional.of(User.from("by-username")),
                                               OptionalInt.of(7),
                                               new ApplicationMetrics(0.5, 0.9),
                                               Optional.of("-----BEGIN PUBLIC KEY-----\n∠( ᐛ 」∠)＿\n-----END PUBLIC KEY-----"),
                                               projectId,
                                               true,
                                               instances);

        Application serialized = APPLICATION_SERIALIZER.fromSlime(APPLICATION_SERIALIZER.toSlime(original));

        assertEquals(original.id(), serialized.id());
        assertEquals(original.createdAt(), serialized.createdAt());

        assertEquals(original.deploymentSpec().xmlForm(), serialized.deploymentSpec().xmlForm());
        assertEquals(original.validationOverrides().xmlForm(), serialized.validationOverrides().xmlForm());

        assertEquals(original.projectId(), serialized.projectId());
        assertEquals(original.internal(), serialized.internal());
        assertEquals(original.deploymentIssueId(), serialized.deploymentIssueId());

        assertEquals(0, serialized.require(id3.instance()).deployments().size());
        assertEquals(0, serialized.require(id3.instance()).deploymentJobs().jobStatus().size());
        assertEquals(0, serialized.require(id3.instance()).rotations().size());
        assertEquals(RotationStatus.EMPTY, serialized.require(id3.instance()).rotationStatus());

        assertEquals(2, serialized.require(id1.instance()).deployments().size());
        assertEquals(original.require(id1.instance()).deployments().get(zone1).applicationVersion(), serialized.require(id1.instance()).deployments().get(zone1).applicationVersion());
        assertEquals(original.require(id1.instance()).deployments().get(zone2).applicationVersion(), serialized.require(id1.instance()).deployments().get(zone2).applicationVersion());
        assertEquals(original.require(id1.instance()).deployments().get(zone1).version(), serialized.require(id1.instance()).deployments().get(zone1).version());
        assertEquals(original.require(id1.instance()).deployments().get(zone2).version(), serialized.require(id1.instance()).deployments().get(zone2).version());
        assertEquals(original.require(id1.instance()).deployments().get(zone1).at(), serialized.require(id1.instance()).deployments().get(zone1).at());
        assertEquals(original.require(id1.instance()).deployments().get(zone2).at(), serialized.require(id1.instance()).deployments().get(zone2).at());
        assertEquals(original.require(id1.instance()).deployments().get(zone2).activity().lastQueried().get(), serialized.require(id1.instance()).deployments().get(zone2).activity().lastQueried().get());
        assertEquals(original.require(id1.instance()).deployments().get(zone2).activity().lastWritten().get(), serialized.require(id1.instance()).deployments().get(zone2).activity().lastWritten().get());

        assertEquals(original.require(id1.instance()).deploymentJobs().projectId(), serialized.require(id1.instance()).deploymentJobs().projectId());
        assertEquals(original.require(id1.instance()).deploymentJobs().jobStatus().size(), serialized.require(id1.instance()).deploymentJobs().jobStatus().size());
        assertEquals(  original.require(id1.instance()).deploymentJobs().jobStatus().get(JobType.systemTest),
                     serialized.require(id1.instance()).deploymentJobs().jobStatus().get(JobType.systemTest));
        assertEquals(  original.require(id1.instance()).deploymentJobs().jobStatus().get(JobType.stagingTest),
                     serialized.require(id1.instance()).deploymentJobs().jobStatus().get(JobType.stagingTest));

        assertEquals(original.outstandingChange(), serialized.outstandingChange());

        assertEquals(original.ownershipIssueId(), serialized.ownershipIssueId());
        assertEquals(original.owner(), serialized.owner());
        assertEquals(original.majorVersion(), serialized.majorVersion());
        assertEquals(original.change(), serialized.change());
        assertEquals(original.pemDeployKey(), serialized.pemDeployKey());

        assertEquals(original.require(id1.instance()).rotations(), serialized.require(id1.instance()).rotations());
        assertEquals(original.require(id1.instance()).rotationStatus(), serialized.require(id1.instance()).rotationStatus());

        // Test cluster utilization
        assertEquals(0, serialized.require(id1.instance()).deployments().get(zone1).clusterUtils().size());
        assertEquals(3, serialized.require(id1.instance()).deployments().get(zone2).clusterUtils().size());
        assertEquals(0.4, serialized.require(id1.instance()).deployments().get(zone2).clusterUtils().get(ClusterSpec.Id.from("id2")).getCpu(), 0.01);
        assertEquals(0.2, serialized.require(id1.instance()).deployments().get(zone2).clusterUtils().get(ClusterSpec.Id.from("id1")).getCpu(), 0.01);
        assertEquals(0.2, serialized.require(id1.instance()).deployments().get(zone2).clusterUtils().get(ClusterSpec.Id.from("id1")).getMemory(), 0.01);

        // Test cluster info
        assertEquals(3, serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().size());
        assertEquals(10, serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorCost());
        assertEquals(ClusterSpec.Type.content, serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getClusterType());
        assertEquals("flavor2", serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavor());
        assertEquals(4, serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getHostnames().size());
        assertEquals(2, serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorCPU(), Double.MIN_VALUE);
        assertEquals(4, serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorMem(), Double.MIN_VALUE);
        assertEquals(50, serialized.require(id1.instance()).deployments().get(zone2).clusterInfo().get(ClusterSpec.Id.from("id2")).getFlavorDisk(), Double.MIN_VALUE);

        // Test metrics
        assertEquals(original.metrics().queryServiceQuality(), serialized.metrics().queryServiceQuality(), Double.MIN_VALUE);
        assertEquals(original.metrics().writeServiceQuality(), serialized.metrics().writeServiceQuality(), Double.MIN_VALUE);
        assertEquals(original.require(id1.instance()).deployments().get(zone2).metrics().queriesPerSecond(), serialized.require(id1.instance()).deployments().get(zone2).metrics().queriesPerSecond(), Double.MIN_VALUE);
        assertEquals(original.require(id1.instance()).deployments().get(zone2).metrics().writesPerSecond(), serialized.require(id1.instance()).deployments().get(zone2).metrics().writesPerSecond(), Double.MIN_VALUE);
        assertEquals(original.require(id1.instance()).deployments().get(zone2).metrics().documentCount(), serialized.require(id1.instance()).deployments().get(zone2).metrics().documentCount(), Double.MIN_VALUE);
        assertEquals(original.require(id1.instance()).deployments().get(zone2).metrics().queryLatencyMillis(), serialized.require(id1.instance()).deployments().get(zone2).metrics().queryLatencyMillis(), Double.MIN_VALUE);
        assertEquals(original.require(id1.instance()).deployments().get(zone2).metrics().writeLatencyMillis(), serialized.require(id1.instance()).deployments().get(zone2).metrics().writeLatencyMillis(), Double.MIN_VALUE);
        assertEquals(original.require(id1.instance()).deployments().get(zone2).metrics().instant(), serialized.require(id1.instance()).deployments().get(zone2).metrics().instant());
        assertEquals(original.require(id1.instance()).deployments().get(zone2).metrics().warnings(), serialized.require(id1.instance()).deployments().get(zone2).metrics().warnings());
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
        APPLICATION_SERIALIZER.fromSlime(SlimeUtils.jsonToSlime(applicationJson));
        // ok if no error
    }

}
