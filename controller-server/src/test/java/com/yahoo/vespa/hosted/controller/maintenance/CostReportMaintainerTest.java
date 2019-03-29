package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryClientMock;
import com.yahoo.vespa.hosted.controller.restapi.cost.CostReportConsumer;
import com.yahoo.vespa.hosted.controller.restapi.cost.config.SelfHostedCostConfig;
import org.junit.Assert;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class CostReportMaintainerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();

        CostReportConsumer mockConsumer = csv -> Assert.assertEquals(csv,
                "Date,Property,Reserved Cpu Cores,Reserved Memory GB,Reserved Disk Space GB,Usage Fraction\n" +
                        "1970-01-01,Property1,96.0,96.0,2000.0,0.3055555555555555\n" +
                        "1970-01-01,Property3,128.0,96.0,2000.0,0.3333333333333333\n" +
                        "1970-01-01,Property2,160.0,96.0,2000.0,0.3611111111111111");

        SelfHostedCostConfig costConfig = new SelfHostedCostConfig.Builder()
                .properties(
                        new SelfHostedCostConfig.Properties.Builder()
                                .name("Property3")
                                .cpuCores(256)
                                .memoryGb(192)
                                .diskGb(4000))
                .build();


        tester.createTenant("tenant1", "app1", 1L);
        tester.createTenant("tenant2", "app2", 2L);
        CostReportMaintainer maintainer = new CostReportMaintainer(
                tester.controller(),
                Duration.ofDays(1),
                mockConsumer,
                new JobControl(tester.curator()),
                new NodeRepositoryClientMock(),
                Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")),
                costConfig);
        maintainer.maintain();
    }
}