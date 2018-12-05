package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryClientMock;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

public class CostReportMaintainerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        tester.createTenant("lsbe", "local-search", 1L);
        tester.createTenant("mediasearch", "msbe", 2L);
        CostReportMaintainer maintainer = new CostReportMaintainer(tester.controller(), Duration.ofDays(1), new JobControl(tester.curator()), new NodeRepositoryClientMock());
        maintainer.maintain();
        Assert.assertEquals(maintainer.csv,
                "Property1,0.4583333333333333\n" +
                        "Property2,0.5416666666666666");
    }
}