// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.authority.config.ApiAuthorityConfig;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;

/**
 * @author smorgrav
 */
public class ClusterUtilizationMaintainerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(4443);

    @Test
    public void maintain() {
        wireMockRule.stubFor(post(urlEqualTo("/metricforwarding/v1/clusterutilization"))
                .willReturn(aResponse().withStatus(200)));
        ControllerTester tester = new ControllerTester();
        ApplicationId app = tester.createAndDeploy("tenant1", "domain1", "app1", Environment.dev, 123).id();

        // Precondition: no cluster info attached to the deployments
        Deployment deployment = tester.controller().applications().get(app).get().deployments().values().stream().findAny().get();
        Assert.assertEquals(0, deployment.clusterUtils().size());

        ApiAuthorityConfig.Builder apiAuthorityConfigBuilder = new ApiAuthorityConfig.Builder().authorities("http://localhost:4443/");
        ApiAuthorityConfig apiAuthorityConfig = new ApiAuthorityConfig(apiAuthorityConfigBuilder);
        ClusterUtilizationMaintainer maintainer = new ClusterUtilizationMaintainer(tester.controller(), Duration.ofHours(1), new JobControl(new MockCuratorDb()), apiAuthorityConfig);
        maintainer.maintain();

        List<ServeEvent> allServeEvents = getAllServeEvents();
        assertEquals(allServeEvents.size(), 1);
        LoggedRequest request = findAll(postRequestedFor(urlEqualTo("/metricforwarding/v1/clusterutilization"))).get(0);
        String expectedBody = "[{\"applicationId\":\"tenant1:app1:default\",\"deployments\":[{\"zoneId\":\"dev.us-east-1\",\"clusterUtil\":[{\"clusterSpecId\":\"default\",\"cpu\":0.5554,\"memory\":0.6990000000000001,\"disk\":0.34590000000000004,\"diskBusy\":0.0}]}]}]";
        assertEquals(expectedBody, new String(request.getBody()));
    }

}
