package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.aws.MockResourceTagger;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class ResourceTagMaintainerTest {

    ControllerTester tester = new ControllerTester();

    @Test
    public void maintain() {
        setUpZones();
        MockResourceTagger mockResourceTagger = new MockResourceTagger();
        ResourceTagMaintainer resourceTagMaintainer = new ResourceTagMaintainer(tester.controller(),
                                                                                Duration.ofMinutes(5),
                                                                                new JobControl(tester.curator()),
                                                                                mockResourceTagger);
        resourceTagMaintainer.maintain();
        assertEquals(2, mockResourceTagger.getValues().size());
        Map<HostName, ApplicationId> applicationForHost = mockResourceTagger.getValues().get(ZoneId.from("prod.region-2"));
        assertEquals(ApplicationId.from("tenant1", "app1", "default"), applicationForHost.get(HostName.from("parentHostA")));
        assertEquals(ApplicationId.from("tenant2", "app2", "default"), applicationForHost.get(HostName.from("parentHostB")));


    }

    private void setUpZones() {
        ZoneApiMock nonAwsZone = ZoneApiMock.newBuilder().withId("test.region-1").build();
        ZoneApiMock awsZone1 = ZoneApiMock.newBuilder().withId("prod.region-2").withCloud("aws").build();
        ZoneApiMock awsZone2 = ZoneApiMock.newBuilder().withId("test.region-3").withCloud("aws").build();
        tester.zoneRegistry().setZones(
                nonAwsZone,
                awsZone1,
                awsZone2);
        tester.configServer().nodeRepository().addFixedNodes(nonAwsZone.getId());
        tester.configServer().nodeRepository().addFixedNodes(awsZone1.getId());
        tester.configServer().nodeRepository().addFixedNodes(awsZone2.getId());
    }

}