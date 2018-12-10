package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryClientMock;
import com.yahoo.vespa.hosted.controller.restapi.cost.CostReportConsumer;
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
                        "1970-01-01,Property1,96.0,96.0,2000.0,0.4583333333333333\n" +
                        "1970-01-01,Property2,160.0,96.0,2000.0,0.5416666666666666");

        tester.createTenant("lsbe", "local-search", 1L);
        tester.createTenant("mediasearch", "msbe", 2L);
        CostReportMaintainer maintainer = new CostReportMaintainer(tester.controller(), Duration.ofDays(1), mockConsumer, new JobControl(tester.curator()), new NodeRepositoryClientMock(), Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")));
        maintainer.maintain();
    }
}