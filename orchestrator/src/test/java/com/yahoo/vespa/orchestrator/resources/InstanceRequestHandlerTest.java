// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpRequestBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.restapi.RestApiTestDriver;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.restapi.wire.SlobrokEntryResponse;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.monitor.SlobrokApi;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
class InstanceRequestHandlerTest {

    private static final String APPLICATION_INSTANCE_REFERENCE = "tenant:app:prod:us-west-1:instance";
    private static final ApplicationId APPLICATION_ID = ApplicationId.from(
            "tenant", "app", "instance");
    private static final List<Mirror.Entry> ENTRIES = Arrays.asList(
            new Mirror.Entry("name1", "tcp/spec:1"),
            new Mirror.Entry("name2", "tcp/spec:2"));
    private static final ClusterId CLUSTER_ID = new ClusterId("cluster-id");

    private final SlobrokApi slobrokApi = mock(SlobrokApi.class);
    private final UnionMonitorManager rootManager = mock(UnionMonitorManager.class);
    private final RestApiTestDriver testDriver =
            RestApiTestDriver.newBuilder(ctx -> new InstanceRequestHandler(ctx, null, null, slobrokApi, rootManager))
                    .build();

    @Test
    void testGetSlobrokEntries() throws Exception {
        testGetSlobrokEntriesWith("foo", "foo");
    }

    @Test
    void testGetSlobrokEntriesWithoutPattern() throws Exception {
        testGetSlobrokEntriesWith(null, InstanceRequestHandler.DEFAULT_SLOBROK_PATTERN);
    }

    @Test
    void testGetServiceStatusInfo() {
        ServiceType serviceType = new ServiceType("serviceType");
        ConfigId configId = new ConfigId("configId");
        ServiceStatus serviceStatus = ServiceStatus.UP;
        when(rootManager.getStatus(APPLICATION_ID, CLUSTER_ID, serviceType, configId))
                .thenReturn(new ServiceStatusInfo(serviceStatus));


        String uriPath = String.format("/orchestrator/v1/instances/%s/serviceStatusInfo", APPLICATION_INSTANCE_REFERENCE);
        HttpRequest request = HttpRequestBuilder.create(GET, uriPath)
                .withQueryParameter("clusterId", CLUSTER_ID.s())
                .withQueryParameter("serviceType", serviceType.s())
                .withQueryParameter("configId", configId.s())
                .build();
        HttpResponse response = testDriver.executeRequest(request);
        assertEquals(200, response.getStatus());
        ServiceStatusInfo serviceStatusInfo = testDriver.parseJacksonResponseContent(response, ServiceStatusInfo.class);

        ServiceStatus actualServiceStatus = serviceStatusInfo.serviceStatus();
        verify(rootManager).getStatus(APPLICATION_ID, CLUSTER_ID, serviceType, configId);
        assertEquals(serviceStatus, actualServiceStatus);
    }

    @Test
    void testBadRequest() {
        String uriPath = String.format("/orchestrator/v1/instances/%s/serviceStatusInfo", APPLICATION_INSTANCE_REFERENCE);
        HttpRequest request = HttpRequestBuilder.create(GET, uriPath)
                .withQueryParameter("clusterId", CLUSTER_ID.s())
                .build();
        HttpResponse response = testDriver.executeRequest(request);
        assertEquals(400, response.getStatus());
    }

    private void testGetSlobrokEntriesWith(String pattern, String expectedLookupPattern)
            throws Exception{
        when(slobrokApi.lookup(APPLICATION_ID, expectedLookupPattern))
                .thenReturn(ENTRIES);

        String uriPath = String.format("/orchestrator/v1/instances/%s/slobrok", APPLICATION_INSTANCE_REFERENCE);
        var builder = HttpRequestBuilder.create(GET, uriPath);
        if (pattern != null) {
            builder.withQueryParameter("pattern", pattern);
        }
        HttpRequest request = builder.build();
        HttpResponse response = testDriver.executeRequest(request);
        assertEquals(200, response.getStatus());
        List<SlobrokEntryResponse> result = testDriver.parseJacksonResponseContent(response, new TypeReference<>() {});

        verify(slobrokApi).lookup(APPLICATION_ID, expectedLookupPattern);

        String actualJson = testDriver.jacksonJsonMapper().writeValueAsString(result);
        assertEquals(
                "[{\"name\":\"name1\",\"spec\":\"tcp/spec:1\"},{\"name\":\"name2\",\"spec\":\"tcp/spec:2\"}]",
                actualJson);
    }

}
