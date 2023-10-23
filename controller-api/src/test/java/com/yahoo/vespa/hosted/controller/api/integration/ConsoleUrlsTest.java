// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
class ConsoleUrlsTest {

    private final ConsoleUrls urls = new ConsoleUrls(URI.create("https://console.tld/"));

    @Test
    void urls() {
        ApplicationId app = ApplicationId.from("t1", "a1", "i1");
        ZoneId prod = ZoneId.from("prod", "us-north-1");
        ZoneId dev = ZoneId.from("dev", "eu-west-2");
        ZoneId test = ZoneId.from("test", "ap-east-3");
        ClusterSpec.Id cluster = ClusterSpec.Id.from("c1");

        assertEquals("https://console.tld", urls.root());
        assertEquals("https://console.tld/tenant/t1", urls.tenantOverview(app.tenant()));
        assertEquals("https://console.tld/tenant/t1/account/notifications", urls.tenantNotifications(app.tenant()));
        assertEquals("https://console.tld/tenant/t1/application/a1/prod/instance", urls.prodApplicationOverview(app.tenant(), app.application()));
        assertEquals("https://console.tld/tenant/t1/application/a1/prod/instance/i1", urls.instanceOverview(app, Environment.test));
        assertEquals("https://console.tld/tenant/t1/application/a1/dev/instance/i1?i1.dev.eu-west-2=clusters%2Cc1", urls.clusterOverview(app, dev, cluster));
        assertEquals("https://console.tld/tenant/t1/application/a1/prod/instance/i1?i1.prod.us-north-1=clusters%2Cc1%3Dreindexing", urls.clusterReindexing(app, prod, cluster));
        assertEquals("https://console.tld/tenant/t1/application/a1/prod/instance/i1/job/production-us-north-1/run/1", urls.deploymentRun(new RunId(app, JobType.deploymentTo(prod), 1)));
        assertEquals("https://console.tld/tenant/t1/application/a1/prod/instance/i1/job/system-test/run/1", urls.deploymentRun(new RunId(app, JobType.deploymentTo(test), 1)));
        assertEquals("https://console.tld/tenant/t1/application/a1/dev/instance/i1/job/dev-eu-west-2/run/1", urls.deploymentRun(new RunId(app, JobType.deploymentTo(dev), 1)));
        assertEquals("https://console.tld/verify?code=test123", urls.verifyEmail("test123"));
    }
}
