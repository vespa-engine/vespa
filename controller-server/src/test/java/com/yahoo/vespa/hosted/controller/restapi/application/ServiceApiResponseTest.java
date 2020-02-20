// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.ServiceView;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ServiceApiResponseTest {

    private final static String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";

    @Test
    public void testServiceViewResponse() throws URISyntaxException, IOException {
        ServiceApiResponse response = new ServiceApiResponse(ZoneId.from(Environment.prod, RegionName.from("us-west-1")),
                                                             ApplicationId.from("tenant1", "application1", "default"),
                                                             Collections.singletonList(new URI("config-server1")),
                                                             new URI("http://server1:4080/request/path?foo=bar"));
        ApplicationView applicationView = new ApplicationView();
        ClusterView clusterView = new ClusterView();
        clusterView.type = "container";
        clusterView.name = "cluster1";
        clusterView.url = "cluster-url";
        ServiceView serviceView = new ServiceView();
        serviceView.url = null;
        serviceView.serviceType = "container";
        serviceView.serviceName = "service1";
        serviceView.configId = "configId1";
        serviceView.host = "host1";
        serviceView.legacyStatusPages = "legacyPages";
        clusterView.services = Collections.singletonList(serviceView);
        applicationView.clusters = Collections.singletonList(clusterView);
        response.setResponse(applicationView);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        response.render(stream);
        Slime responseSlime = SlimeUtils.jsonToSlime(stream.toByteArray());
        Slime expectedSlime = SlimeUtils.jsonToSlime(IOUtils.readFile(new File(responseFiles + "service-api-response.json")).getBytes(StandardCharsets.UTF_8));

        assertEquals("service-api-response.json",
                     new String(SlimeUtils.toJsonBytes(expectedSlime), StandardCharsets.UTF_8),
                     new String(SlimeUtils.toJsonBytes(responseSlime), StandardCharsets.UTF_8));
    }

    @Test
    public void testServiceViewResponseWithURLs() throws URISyntaxException, IOException {
        ServiceApiResponse response = new ServiceApiResponse(ZoneId.from(Environment.prod, RegionName.from("us-west-1")),
                                                             ApplicationId.from("tenant2", "application2", "default"),
                                                             Collections.singletonList(new URI("http://cfg1.test/")),
                                                             new URI("http://cfg1.test/serviceview/v1/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/service/searchnode-9dujk1pa0vufxrj6n4yvmi8uc/state/v1"));
        ApplicationView applicationView = new ApplicationView();
        ClusterView clusterView = new ClusterView();
        clusterView.type = "container";
        clusterView.name = "cluster1";
        clusterView.url = "http://cfg1.test/serviceview/v1/tenant/tenant2/application/application2/environment/prod/region/us-west-1/instance/default/service/searchnode-9dujk1pa0vufxrj6n4yvmi8uc/state/v1/health";
        ServiceView serviceView = new ServiceView();
        serviceView.url = null;
        serviceView.serviceType = "container";
        serviceView.serviceName = "service1";
        serviceView.configId = "configId1";
        serviceView.host = "host1";
        serviceView.legacyStatusPages = "legacyPages";
        clusterView.services = Collections.singletonList(serviceView);
        applicationView.clusters = Collections.singletonList(clusterView);
        response.setResponse(applicationView);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        response.render(stream);
        Slime responseSlime = SlimeUtils.jsonToSlime(stream.toByteArray());
        Slime expectedSlime = SlimeUtils.jsonToSlime(IOUtils.readFile(new File(responseFiles + "service-api-response-with-urls.json")).getBytes(StandardCharsets.UTF_8));

        assertEquals("service-api-response.json",
                     new String(SlimeUtils.toJsonBytes(expectedSlime), StandardCharsets.UTF_8),
                     new String(SlimeUtils.toJsonBytes(responseSlime), StandardCharsets.UTF_8));
    }

}
