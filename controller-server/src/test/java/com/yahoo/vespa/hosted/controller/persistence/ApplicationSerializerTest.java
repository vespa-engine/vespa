// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.ApplicationRevision;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class ApplicationSerializerTest {

    private static final ApplicationSerializer applicationSerializer = new ApplicationSerializer();

    private static final Zone zone1 = new Zone(Environment.from("prod"), RegionName.from("us-west-1"));
    private static final Zone zone2 = new Zone(Environment.from("prod"), RegionName.from("us-east-3"));

    @Test
    public void testSerialization() {
        ControllerTester tester = new ControllerTester();
        DeploymentSpec deploymentSpec = DeploymentSpec.fromXml("<deployment version='1.0'>" +
                                                               "   <staging/>" +
                                                               "</deployment>");
        ValidationOverrides validationOverrides = ValidationOverrides.fromXml("<validation-overrides version='1.0'>" +
                                                                              "  <allow until='2017-06-15'>deployment-removal</allow>" +
                                                                              "</validation-overrides>");

        List<Deployment> deployments = new ArrayList<>();
        ApplicationRevision revision1 = ApplicationRevision.from("appHash1");
        ApplicationRevision revision2 = ApplicationRevision.from("appHash2", new SourceRevision("repo1", "branch1", "commit1"));
        deployments.add(new Deployment(zone1, revision1, Version.fromString("1.2.3"), Instant.ofEpochMilli(3))); // One deployment without cluster info and utils
        deployments.add(new Deployment(zone2, revision2, Version.fromString("1.2.3"), Instant.ofEpochMilli(5),
                createClusterUtils(3, 0.2), createClusterInfo(3, 4),new DeploymentMetrics(2,3,4,5,6)));

        Optional<Long> projectId = Optional.of(123L);
        List<JobStatus> statusList = new ArrayList<>();

        statusList.add(JobStatus.initial(DeploymentJobs.JobType.systemTest)
                                .withTriggering(Version.fromString("5.6.7"), Optional.empty(), true, Instant.ofEpochMilli(7))
                                .withCompletion(Optional.empty(), Instant.ofEpochMilli(8), tester.controller()));
        statusList.add(JobStatus.initial(DeploymentJobs.JobType.stagingTest)
                                .withTriggering(Version.fromString("5.6.6"), Optional.empty(), true, Instant.ofEpochMilli(5))
                                .withCompletion(Optional.of(JobError.unknown), Instant.ofEpochMilli(6), tester.controller()));

        DeploymentJobs deploymentJobs = new DeploymentJobs(projectId, statusList, Optional.empty());

        Application original = new Application(ApplicationId.from("t1", "a1", "i1"),
                                               deploymentSpec,
                                               validationOverrides,
                                               deployments, deploymentJobs,
                                               Optional.of(new Change.VersionChange(Version.fromString("6.7"))),
                                               true);

        Application serialized = applicationSerializer.fromSlime(applicationSerializer.toSlime(original));

        assertEquals(original.id(), serialized.id());

        assertEquals(original.deploymentSpec().xmlForm(), serialized.deploymentSpec().xmlForm());
        assertEquals(original.validationOverrides().xmlForm(), serialized.validationOverrides().xmlForm());

        assertEquals(2, serialized.deployments().size());
        assertEquals(original.deployments().get(zone1).revision(), serialized.deployments().get(zone1).revision());
        assertEquals(original.deployments().get(zone2).revision(), serialized.deployments().get(zone2).revision());
        assertEquals(original.deployments().get(zone1).version(), serialized.deployments().get(zone1).version());
        assertEquals(original.deployments().get(zone2).version(), serialized.deployments().get(zone2).version());
        assertEquals(original.deployments().get(zone1).at(), serialized.deployments().get(zone1).at());
        assertEquals(original.deployments().get(zone2).at(), serialized.deployments().get(zone2).at());

        assertEquals(original.deploymentJobs().projectId(), serialized.deploymentJobs().projectId());
        assertEquals(original.deploymentJobs().jobStatus().size(), serialized.deploymentJobs().jobStatus().size());
        assertEquals(  original.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest),
                     serialized.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest));
        assertEquals(  original.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.stagingTest),
                     serialized.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.stagingTest));
        assertEquals(original.deploymentJobs().failingSince(), serialized.deploymentJobs().failingSince());

        assertEquals(original.hasOutstandingChange(), serialized.hasOutstandingChange());

        assertEquals(original.deploying(), serialized.deploying());

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
        assertEquals(2, serialized.deployments().get(zone2).metrics().queriesPerSecond(), Double.MIN_VALUE);
        assertEquals(3, serialized.deployments().get(zone2).metrics().writesPerSecond(), Double.MIN_VALUE);
        assertEquals(4, serialized.deployments().get(zone2).metrics().documentCount(), Double.MIN_VALUE);
        assertEquals(5, serialized.deployments().get(zone2).metrics().queryLatencyMillis(), Double.MIN_VALUE);
        assertEquals(6, serialized.deployments().get(zone2).metrics().writeLatencyMillis(), Double.MIN_VALUE);

        { // test more deployment serialization cases
            Application original2 = original.withDeploying(Optional.of(Change.ApplicationChange.of(ApplicationRevision.from("hash1"))));
            Application serialized2 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original2));
            assertEquals(original2.deploying(), serialized2.deploying());
            assertEquals(((Change.ApplicationChange)serialized2.deploying().get()).revision().get().source(),
                         ((Change.ApplicationChange)original2.deploying().get()).revision().get().source());

            Application original3 = original.withDeploying(Optional.of(Change.ApplicationChange.of(ApplicationRevision.from("hash1",
                                                                                                                            new SourceRevision("a", "b", "c")))));
            Application serialized3 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original3));
            assertEquals(original3.deploying(), serialized2.deploying());
            assertEquals(((Change.ApplicationChange)serialized3.deploying().get()).revision().get().source(),
                         ((Change.ApplicationChange)original3.deploying().get()).revision().get().source());

            Application original4 = original.withDeploying(Optional.empty());
            Application serialized4 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original4));
            assertEquals(original4.deploying(), serialized4.deploying());
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
    public void testLegacySerialization() throws IOException {
        Application applicationWithSuccessfulJob = applicationSerializer.fromSlime(applicationSlime(false));
        assertFalse("No job error for successful job", applicationWithSuccessfulJob.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest).jobError().isPresent());

        Application applicationWithFailingJob = applicationSerializer.fromSlime(applicationSlime(true));
        assertEquals(JobError.unknown, applicationWithFailingJob.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest).jobError().get());
    }

    @Test
    public void testLegacySerializationWithoutUpgradeField() {
        Application application = applicationSerializer.fromSlime(applicationSlime(false));
        assertFalse(application.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest).lastCompleted().get().upgrade());
    }

    // TODO: Remove after October 2017
    @Test
    public void testLegacySerializationWithZeroProjectId() {
        Application original = applicationSerializer.fromSlime(applicationSlime(0, false));
        assertFalse(original.deploymentJobs().projectId().isPresent());
        Application serialized = applicationSerializer.fromSlime(applicationSerializer.toSlime(original));
        assertFalse(serialized.deploymentJobs().projectId().isPresent());
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
