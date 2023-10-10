// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class ExampleModelTest {
    @Test
    public void testEmptyApplication() {
        ApplicationInfo application = ExampleModel
                .createApplication(
                        "tenant",
                        "app")
                .build();

        assertEquals("tenant.app", application.getApplicationId().toString());
        assertEquals(1, application.getGeneration());
        assertEquals(0, application.getModel().getHosts().size());
    }

    @Test
    public void test() {
        List<String> contentNodes = Stream.of("host1", "host2").toList();
        List<String> containerNodes = Stream.of("host3", "host4").toList();

        ApplicationInfo application = ExampleModel
                .createApplication(
                        "tenant",
                        "app")
                .addServiceCluster(
                        "product-controllers",
                        "container-clustercontroller.1",
                        "container-clustercontroller",
                        contentNodes)
                .then()
                .addServiceCluster(
                        "product",
                        "searchnode.1",
                        "searchnode",
                        contentNodes)
                .then()
                .addServiceCluster(
                        "admin",
                        "slobrok.1",
                        "slobrok",
                        containerNodes)
                .then()
                .addServiceCluster(
                        "default",
                        "container.1",
                        "container",
                        containerNodes)
                .then()
                .build();

        assertEquals("tenant.app", application.getApplicationId().toString());

        Collection<HostInfo> hostInfos = application.getModel().getHosts();
        assertEquals(containerNodes.size() + contentNodes.size(), hostInfos.size());

        HostInfo host1 = hostInfos.stream()
                .filter(hostInfo -> hostInfo.getHostname().equals("host1"))
                .findAny()
                .orElseThrow(() -> new RuntimeException());
        ServiceInfo controller1 = host1.getServices().stream()
                .filter(i -> i.getServiceType().equals("container-clustercontroller"))
                .findAny()
                .orElseThrow(() -> new RuntimeException());

        assertEquals("container-clustercontroller", controller1.getServiceType());
        assertEquals("configid/1", controller1.getConfigId());

        HostInfo host4 = hostInfos.stream()
                .filter(hostInfo -> hostInfo.getHostname().equals("host4"))
                .findAny()
                .orElseThrow(() -> new RuntimeException());
        ServiceInfo slobrok2 = host4.getServices().stream()
                .filter(i -> i.getServiceType().equals("slobrok"))
                .findAny()
                .orElseThrow(() -> new RuntimeException());
        assertEquals("configid/2", slobrok2.getConfigId());
    }
}
