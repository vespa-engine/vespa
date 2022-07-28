package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistryMock;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClientMock;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author olaa
 */
public class MeteringMonitorMaintainerTest {

    private ControllerTester tester;
    private DeploymentTester deploymentTester;
    private MetricsMock metrics;
    private ResourceDatabaseClientMock database;
    private MeteringMonitorMaintainer maintainer;
    private final ApplicationId applicationId = ApplicationId.from("foo", "bar", "default");
    private final ZoneId zone = ZoneId.from("prod.aws-us-east-1c");

    @BeforeEach
    public void setup() {
        tester = new ControllerTester(SystemName.Public);
        deploymentTester = new DeploymentTester(tester);
        metrics = new MetricsMock();
        database = new ResourceDatabaseClientMock(new PlanRegistryMock());
        maintainer = new MeteringMonitorMaintainer(tester.controller(), Duration.ofMinutes(5), database, metrics);
    }

    @Test
    void finds_stale_data() {
        deploymentTester.newDeploymentContext(applicationId).submit().deploy();
        maintainer.maintain();
        var now = tester.clock().instant().getEpochSecond();
        var lastSnapshot = tester.serviceRegistry().resourceDatabase().getOldestSnapshotTimestamp(Set.of()).getEpochSecond();
        assertEquals(now - lastSnapshot, metrics.getMetric(MeteringMonitorMaintainer.METERING_AGE_METRIC_NAME));
    }

}