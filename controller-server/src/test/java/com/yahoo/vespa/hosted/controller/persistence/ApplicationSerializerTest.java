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
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.ControllerTester.writable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class ApplicationSerializerTest {

    private static final ApplicationSerializer applicationSerializer = new ApplicationSerializer();

    private static final ZoneId zone1 = ZoneId.from("prod", "us-west-1");
    private static final ZoneId zone2 = ZoneId.from("prod", "us-east-3");

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
        ApplicationVersion applicationVersion1 = ApplicationVersion.from("appHash1");
        ApplicationVersion applicationVersion2 = ApplicationVersion
                .from("appHash2", new SourceRevision("repo1", "branch1", "commit1"));
        deployments.add(new Deployment(zone1, applicationVersion1, Version.fromString("1.2.3"), Instant.ofEpochMilli(3))); // One deployment without cluster info and utils
        deployments.add(new Deployment(zone2, applicationVersion2, Version.fromString("1.2.3"), Instant.ofEpochMilli(5),
                createClusterUtils(3, 0.2), createClusterInfo(3, 4),new DeploymentMetrics(2,3,4,5,6)));

        Optional<Long> projectId = Optional.of(123L);
        List<JobStatus> statusList = new ArrayList<>();

        statusList.add(JobStatus.initial(DeploymentJobs.JobType.systemTest)
                                .withTriggering(Version.fromString("5.6.7"), ApplicationVersion.unknown, true, "Test", Instant.ofEpochMilli(7))
                                .withCompletion(30, Optional.empty(), Instant.ofEpochMilli(8), tester.controller()));
        statusList.add(JobStatus.initial(DeploymentJobs.JobType.stagingTest)
                                .withTriggering(Version.fromString("5.6.6"), ApplicationVersion.unknown, true, "Test 2", Instant.ofEpochMilli(5))
                                .withCompletion(11, Optional.of(JobError.unknown), Instant.ofEpochMilli(6), tester.controller()));

        DeploymentJobs deploymentJobs = new DeploymentJobs(projectId, statusList, Optional.empty());

        Application original = new Application(ApplicationId.from("t1", "a1", "i1"),
                                               deploymentSpec,
                                               validationOverrides,
                                               deployments, deploymentJobs,
                                               new Change.VersionChange(Version.fromString("6.7")),
                                               true,
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

        assertEquals(original.deploymentJobs().projectId(), serialized.deploymentJobs().projectId());
        assertEquals(original.deploymentJobs().jobStatus().size(), serialized.deploymentJobs().jobStatus().size());
        assertEquals(  original.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest),
                     serialized.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.systemTest));
        assertEquals(  original.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.stagingTest),
                     serialized.deploymentJobs().jobStatus().get(DeploymentJobs.JobType.stagingTest));

        assertEquals(original.hasOutstandingChange(), serialized.hasOutstandingChange());

        assertEquals(original.ownershipIssueId(), serialized.ownershipIssueId());

        assertEquals(original.deploying(), serialized.deploying());
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

        assertEquals(2, serialized.deployments().get(zone2).metrics().queriesPerSecond(), Double.MIN_VALUE);
        assertEquals(3, serialized.deployments().get(zone2).metrics().writesPerSecond(), Double.MIN_VALUE);
        assertEquals(4, serialized.deployments().get(zone2).metrics().documentCount(), Double.MIN_VALUE);
        assertEquals(5, serialized.deployments().get(zone2).metrics().queryLatencyMillis(), Double.MIN_VALUE);
        assertEquals(6, serialized.deployments().get(zone2).metrics().writeLatencyMillis(), Double.MIN_VALUE);

        { // test more deployment serialization cases
            Application original2 = writable(original).withDeploying(Change.ApplicationChange.of(ApplicationVersion.from("hash1")));
            Application serialized2 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original2));
            assertEquals(original2.deploying(), serialized2.deploying());
            assertEquals(((Change.ApplicationChange)serialized2.deploying()).version().source(),
                         ((Change.ApplicationChange)original2.deploying()).version().source());

            Application original3 = writable(original).withDeploying(Change.ApplicationChange.of(ApplicationVersion.from("hash1",
                                                                                                                         new SourceRevision("a", "b", "c"))));
            Application serialized3 = applicationSerializer.fromSlime(applicationSerializer.toSlime(original3));
            assertEquals(original3.deploying(), serialized2.deploying());
            assertEquals(((Change.ApplicationChange)serialized3.deploying()).version().source(),
                         ((Change.ApplicationChange)original3.deploying()).version().source());

            Application original4 = writable(original).withDeploying(Change.empty());
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
    public void testLegacySerialization() {
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

    @Test
    public void testCompleteApplicationDeserialization() {
        applicationSerializer.fromSlime(SlimeUtils.jsonToSlime(longApplicationJson.getBytes(StandardCharsets.UTF_8)));
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

    private final String longApplicationJson = "{\"id\":\"tripod:service-aggregation-vespa:default\",\"deploymentSpecField\":\"<deployment version='1.0'>\\n  <test />\\n  <!--<staging />-->\\n  <prod global-service-id=\\\"tripod\\\">\\n    <region active=\\\"true\\\">us-east-3</region>\\n    <region active=\\\"true\\\">us-west-1</region>\\n  </prod>\\n</deployment>\\n\",\"validationOverrides\":\"<validation-overrides>\\n    <allow until=\\\"2016-04-28\\\" comment=\\\"Renaming content cluster\\\">content-cluster-removal</allow>\\n    <allow until=\\\"2016-08-22\\\" comment=\\\"Migrating us-east-3 to C-2E\\\">cluster-size-reduction</allow>\\n    <allow until=\\\"2017-06-30\\\" comment=\\\"Test Vespa upgrade tests\\\">force-automatic-tenant-upgrade-test</allow>\\n</validation-overrides>\\n\",\"deployments\":[{\"zone\":{\"environment\":\"prod\",\"region\":\"us-west-1\"},\"version\":\"6.173.62\",\"deployTime\":1510837817704,\"applicationPackageRevision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"clusterInfo\":{\"tripod\":{\"flavor\":\"d-3-16-100\",\"cost\":9,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"container\",\"hostnames\":[\"oxy-oxygen-2001-4998-c-2942--10d1.gq1.yahoo.com\",\"oxy-oxygen-2001-4998-c-2942--10e2.gq1.yahoo.com\"]},\"tripodaggregation\":{\"flavor\":\"d-12-64-400\",\"cost\":38,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"content\",\"hostnames\":[\"oxy-oxygen-2001-4998-c-2941--106a.gq1.yahoo.com\",\"zt74700-v6-23.ostk.bm2.prod.gq1.yahoo.com\",\"zt74714-v6-28.ostk.bm2.prod.gq1.yahoo.com\",\"zt74730-v6-13.ostk.bm2.prod.gq1.yahoo.com\",\"zt74717-v6-7.ostk.bm2.prod.gq1.yahoo.com\",\"2080260-v6-12.ostk.bm2.prod.gq1.yahoo.com\",\"zt74719-v6-23.ostk.bm2.prod.gq1.yahoo.com\",\"zt74722-v6-26.ostk.bm2.prod.gq1.yahoo.com\",\"zt74704-v6-9.ostk.bm2.prod.gq1.yahoo.com\",\"oxy-oxygen-2001-4998-c-2942--107d.gq1.yahoo.com\"]},\"tripodaggregationstream\":{\"flavor\":\"d-12-64-400\",\"cost\":38,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"content\",\"hostnames\":[\"zt74727-v6-21.ostk.bm2.prod.gq1.yahoo.com\",\"zt74773-v6-8.ostk.bm2.prod.gq1.yahoo.com\",\"zt74699-v6-25.ostk.bm2.prod.gq1.yahoo.com\",\"zt74766-v6-27.ostk.bm2.prod.gq1.yahoo.com\"]}},\"clusterUtils\":{\"tripod\":{\"cpu\":0.1720353499228221,\"mem\":0.4986146831512451,\"disk\":0.0617671330041831,\"diskbusy\":0},\"tripodaggregation\":{\"cpu\":0.07505730001866318,\"mem\":0.7936344432830811,\"disk\":0.2260549694485994,\"diskbusy\":0},\"tripodaggregationstream\":{\"cpu\":0.01712671480989384,\"mem\":0.0225852754983035,\"disk\":0.006084436856721915,\"diskbusy\":0}},\"metrics\":{\"queriesPerSecond\":1.25,\"writesPerSecond\":43.83199977874756,\"documentCount\":525880277.9999999,\"queryLatencyMillis\":5.607503938674927,\"writeLatencyMillis\":20.57866265104621}},{\"zone\":{\"environment\":\"test\",\"region\":\"us-east-1\"},\"version\":\"6.173.62\",\"deployTime\":1511256872316,\"applicationPackageRevision\":{\"applicationPackageHash\":\"ec548fa61cbfab7a270a51d46b1263ec1be5d9a8\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"234f3e4e77049d0b9538c9e1b356d17eb1dedb6a\"}},\"clusterInfo\":{},\"clusterUtils\":{},\"metrics\":{\"queriesPerSecond\":0,\"writesPerSecond\":0,\"documentCount\":0,\"queryLatencyMillis\":0,\"writeLatencyMillis\":0}},{\"zone\":{\"environment\":\"dev\",\"region\":\"us-east-1\"},\"version\":\"6.173.62\",\"deployTime\":1510597489464,\"applicationPackageRevision\":{\"applicationPackageHash\":\"59b883f263c2a3c23dfab249730097d7e0e1ed32\"},\"clusterInfo\":{\"tripod\":{\"flavor\":\"d-2-8-50\",\"cost\":5,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"container\",\"hostnames\":[\"zt40807-v6-29.ostk.bm2.prod.bf1.yahoo.com\"]},\"tripodaggregation\":{\"flavor\":\"d-2-8-50\",\"cost\":5,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"content\",\"hostnames\":[\"zt40807-v6-24.ostk.bm2.prod.bf1.yahoo.com\"]},\"tripodaggregationstream\":{\"flavor\":\"d-2-8-50\",\"cost\":5,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"content\",\"hostnames\":[\"zt40694-v6-21.ostk.bm2.prod.bf1.yahoo.com\"]}},\"clusterUtils\":{\"tripod\":{\"cpu\":0.191833330678661,\"mem\":0.4625738318415235,\"disk\":0.05582004563850269,\"diskbusy\":0},\"tripodaggregation\":{\"cpu\":0.2227037978608054,\"mem\":0.2051752598416401,\"disk\":0.05471533698695047,\"diskbusy\":0},\"tripodaggregationstream\":{\"cpu\":0.1869410834020498,\"mem\":0.1691722576000564,\"disk\":0.04977374774258153,\"diskbusy\":0}},\"metrics\":{\"queriesPerSecond\":0,\"writesPerSecond\":0,\"documentCount\":30916,\"queryLatencyMillis\":0,\"writeLatencyMillis\":0}},{\"zone\":{\"environment\":\"prod\",\"region\":\"us-east-3\"},\"version\":\"6.173.62\",\"deployTime\":1510817190016,\"applicationPackageRevision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"clusterInfo\":{\"tripod\":{\"flavor\":\"d-3-16-100\",\"cost\":9,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"container\",\"hostnames\":[\"zt40738-v6-13.ostk.bm2.prod.bf1.yahoo.com\",\"zt40783-v6-31.ostk.bm2.prod.bf1.yahoo.com\"]},\"tripodaggregation\":{\"flavor\":\"d-12-64-400\",\"cost\":38,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"content\",\"hostnames\":[\"zt40819-v6-7.ostk.bm2.prod.bf1.yahoo.com\",\"zt40661-v6-3.ostk.bm2.prod.bf1.yahoo.com\",\"zt40805-v6-30.ostk.bm2.prod.bf1.yahoo.com\",\"zt40702-v6-32.ostk.bm2.prod.bf1.yahoo.com\",\"zt40706-v6-3.ostk.bm2.prod.bf1.yahoo.com\",\"zt40691-v6-27.ostk.bm2.prod.bf1.yahoo.com\",\"zt40676-v6-15.ostk.bm2.prod.bf1.yahoo.com\",\"zt40788-v6-23.ostk.bm2.prod.bf1.yahoo.com\",\"zt40782-v6-30.ostk.bm2.prod.bf1.yahoo.com\",\"zt40802-v6-32.ostk.bm2.prod.bf1.yahoo.com\"]},\"tripodaggregationstream\":{\"flavor\":\"d-12-64-400\",\"cost\":38,\"flavorCpu\":0,\"flavorMem\":0,\"flavorDisk\":0,\"clusterType\":\"content\",\"hostnames\":[\"zt40779-v6-27.ostk.bm2.prod.bf1.yahoo.com\",\"zt40791-v6-15.ostk.bm2.prod.bf1.yahoo.com\",\"zt40733-v6-31.ostk.bm2.prod.bf1.yahoo.com\",\"zt40724-v6-30.ostk.bm2.prod.bf1.yahoo.com\"]}},\"clusterUtils\":{\"tripod\":{\"cpu\":0.2295038983007097,\"mem\":0.4627357390237263,\"disk\":0.05559941525894966,\"diskbusy\":0},\"tripodaggregation\":{\"cpu\":0.05340429087579549,\"mem\":0.8107630891552372,\"disk\":0.226444914138854,\"diskbusy\":0},\"tripodaggregationstream\":{\"cpu\":0.02148227413975218,\"mem\":0.02162174219104161,\"disk\":0.006057760545243265,\"diskbusy\":0}},\"metrics\":{\"queriesPerSecond\":1.734000012278557,\"writesPerSecond\":44.59999895095825,\"documentCount\":525868193.9999999,\"queryLatencyMillis\":5.65284947195106,\"writeLatencyMillis\":17.34593812832452}}],\"deploymentJobs\":{\"projectId\":102889,\"jobStatus\":[{\"jobType\":\"staging-test\",\"lastTriggered\":{\"id\":-1,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"system-test completed\",\"at\":1510830134259},\"lastCompleted\":{\"id\":1184,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"system-test completed\",\"at\":1510830684960},\"lastSuccess\":{\"id\":1184,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"system-test completed\",\"at\":1510830684960}},{\"jobType\":\"component\",\"lastCompleted\":{\"id\":849,\"version\":\"6.174.156\",\"upgrade\":false,\"reason\":\"Application commit\",\"at\":1511217733555},\"lastSuccess\":{\"id\":849,\"version\":\"6.174.156\",\"upgrade\":false,\"reason\":\"Application commit\",\"at\":1511217733555}},{\"jobType\":\"production-us-east-3\",\"lastTriggered\":{\"id\":-1,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"staging-test completed\",\"at\":1510830685127},\"lastCompleted\":{\"id\":923,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"staging-test completed\",\"at\":1510837650046},\"lastSuccess\":{\"id\":923,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"staging-test completed\",\"at\":1510837650046}},{\"jobType\":\"production-us-west-1\",\"lastTriggered\":{\"id\":-1,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"production-us-east-3 completed\",\"at\":1510837650139},\"lastCompleted\":{\"id\":646,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"production-us-east-3 completed\",\"at\":1510843559162},\"lastSuccess\":{\"id\":646,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"production-us-east-3 completed\",\"at\":1510843559162}},{\"jobType\":\"system-test\",\"jobError\":\"unknown\",\"lastTriggered\":{\"id\":-1,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"ec548fa61cbfab7a270a51d46b1263ec1be5d9a8\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"234f3e4e77049d0b9538c9e1b356d17eb1dedb6a\"}},\"upgrade\":false,\"reason\":\"Available change in component\",\"at\":1511256608649},\"lastCompleted\":{\"id\":1686,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"ec548fa61cbfab7a270a51d46b1263ec1be5d9a8\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"234f3e4e77049d0b9538c9e1b356d17eb1dedb6a\"}},\"upgrade\":false,\"reason\":\"Available change in component\",\"at\":1511256603353},\"firstFailing\":{\"id\":1659,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"ec548fa61cbfab7a270a51d46b1263ec1be5d9a8\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"234f3e4e77049d0b9538c9e1b356d17eb1dedb6a\"}},\"upgrade\":false,\"reason\":\"component completed\",\"at\":1511219070725},\"lastSuccess\":{\"id\":1658,\"version\":\"6.173.62\",\"revision\":{\"applicationPackageHash\":\"9db423e1021d7b452d37ec6372bc757d9c1bda87\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"49cd7bbb1ed9f4b922083cb042590b0885ffe22b\"}},\"upgrade\":true,\"reason\":\"Upgrading to 6.173.62\",\"at\":1511175754163}}]},\"deployingField\":{\"applicationPackageHash\":\"ec548fa61cbfab7a270a51d46b1263ec1be5d9a8\",\"sourceRevision\":{\"repositoryField\":\"git@git.ouroath.com:Tripod/service-aggregation-vespa.git\",\"branchField\":\"origin/master\",\"commitField\":\"234f3e4e77049d0b9538c9e1b356d17eb1dedb6a\"}},\"outstandingChangeField\":false,\"queryQuality\":100,\"writeQuality\":99.99894341115082}";
}
