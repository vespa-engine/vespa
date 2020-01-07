// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.security.KeyUtils;
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
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentActivity;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationState;
import com.yahoo.vespa.hosted.controller.rotation.RotationStatus;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
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

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */

public class ApplicationSerializerTest {

    private static final ApplicationSerializer APPLICATION_SERIALIZER = new ApplicationSerializer();
    private static final Path testData = Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/persistence/testdata/");
    private static final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private static final ZoneId zone2 = ZoneId.from("prod", "us-east-3");
    private static final PublicKey publicKey = KeyUtils.fromPemEncodedPublicKey("-----BEGIN PUBLIC KEY-----\n" +
                                                                                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                                                                "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                                                "-----END PUBLIC KEY-----\n");
    private static final PublicKey otherPublicKey = KeyUtils.fromPemEncodedPublicKey("-----BEGIN PUBLIC KEY-----\n" +
                                                                                     "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFELzPyinTfQ/sZnTmRp5E4Ve/sbE\n" +
                                                                                     "pDhJeqczkyFcT2PysJ5sZwm7rKPEeXDOhzTPCyRvbUqc2SGdWbKUGGa/Yw==\n" +
                                                                                     "-----END PUBLIC KEY-----\n");


    @Test
    public void testSerialization() throws Exception {
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml("<deployment version='1.0'>\n" +
                                                               "   <staging/>\n" +
                                                               "   <instance id=\"i1\">\n" +
                                                               "      <prod global-service-id=\"default\">\n" +
                                                               "         <region active=\"true\">us-west-1</region>\n" +
                                                               "      </prod>\n" +
                                                               "   </instance>\n" +
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
                                       createClusterInfo(3, 4),
                                       new DeploymentMetrics(2, 3, 4, 5, 6,
                                                             Optional.of(Instant.now().truncatedTo(ChronoUnit.MILLIS)),
                                                             Map.of(DeploymentMetrics.Warning.all, 3)),
                                       DeploymentActivity.create(Optional.of(activityAt), Optional.of(activityAt),
                                                                 OptionalDouble.of(200), OptionalDouble.of(10))));

        var rotationStatus = RotationStatus.from(Map.of(new RotationId("my-rotation"),
                                                        new RotationStatus.Targets(
                                                                Map.of(ZoneId.from("prod", "us-west-1"), RotationState.in,
                                                                       ZoneId.from("prod", "us-east-3"), RotationState.out),
                                                                Instant.ofEpochMilli(42))));

        ApplicationId id1 = ApplicationId.from("t1", "a1", "i1");
        ApplicationId id3 = ApplicationId.from("t1", "a1", "i3");
        List<Instance> instances = List.of(new Instance(id1,
                                                        deployments,
                                                        Map.of(JobType.systemTest, Instant.ofEpochMilli(333)),
                                                        List.of(AssignedRotation.fromStrings("foo", "default", "my-rotation", Set.of("us-west-1"))),
                                                        rotationStatus,
                                                        Change.of(new Version("6.1"))),
                                           new Instance(id3,
                                                        List.of(),
                                                        Map.of(),
                                                        List.of(),
                                                        RotationStatus.EMPTY,
                                                        Change.of(Version.fromString("6.7")).withPin()));

        Application original = new Application(TenantAndApplicationId.from(id1),
                                               Instant.now().truncatedTo(ChronoUnit.MILLIS),
                                               deploymentSpec,
                                               validationOverrides,
                                               Optional.of(IssueId.from("4321")),
                                               Optional.of(IssueId.from("1234")),
                                               Optional.of(User.from("by-username")),
                                               OptionalInt.of(7),
                                               new ApplicationMetrics(0.5, 0.9),
                                               Set.of(publicKey, otherPublicKey),
                                               projectId,
                                               Optional.of(applicationVersion1),
                                               instances);

        Application serialized = APPLICATION_SERIALIZER.fromSlime(SlimeUtils.toJsonBytes(APPLICATION_SERIALIZER.toSlime(original)));

        assertEquals(original.id(), serialized.id());
        assertEquals(original.createdAt(), serialized.createdAt());
        assertEquals(original.latestVersion(), serialized.latestVersion());

        assertEquals(original.deploymentSpec().xmlForm(), serialized.deploymentSpec().xmlForm());
        assertEquals(original.validationOverrides().xmlForm(), serialized.validationOverrides().xmlForm());

        assertEquals(original.projectId(), serialized.projectId());
        assertEquals(original.deploymentIssueId(), serialized.deploymentIssueId());

        assertEquals(0, serialized.require(id3.instance()).deployments().size());
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

        assertEquals(original.require(id1.instance()).jobPause(JobType.systemTest),
                     serialized.require(id1.instance()).jobPause(JobType.systemTest));
        assertEquals(original.require(id1.instance()).jobPause(JobType.stagingTest),
                     serialized.require(id1.instance()).jobPause(JobType.stagingTest));

        assertEquals(original.ownershipIssueId(), serialized.ownershipIssueId());
        assertEquals(original.owner(), serialized.owner());
        assertEquals(original.majorVersion(), serialized.majorVersion());
        assertEquals(original.deployKeys(), serialized.deployKeys());

        assertEquals(original.require(id1.instance()).rotations(), serialized.require(id1.instance()).rotations());
        assertEquals(original.require(id1.instance()).rotationStatus(), serialized.require(id1.instance()).rotationStatus());

        assertEquals(original.require(id1.instance()).change(), serialized.require(id1.instance()).change());
        assertEquals(original.require(id3.instance()).change(), serialized.require(id3.instance()).change());

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

    @Test
    public void testCompleteApplicationDeserialization() throws Exception {
        byte[] applicationJson = Files.readAllBytes(testData.resolve("complete-application.json"));
        APPLICATION_SERIALIZER.fromSlime(applicationJson);
        // ok if no error
    }

}
