// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.restapi.wire.SlobrokEntryResponse;
import com.yahoo.vespa.service.manager.UnionMonitorManager;
import com.yahoo.vespa.service.monitor.SlobrokApi;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

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
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module());

    private final SlobrokApi slobrokApi = mock(SlobrokApi.class);
    private final UnionMonitorManager rootManager = mock(UnionMonitorManager.class);
    private final InstanceRequestHandler handler = new InstanceRequestHandler(
            new LoggingRequestHandler.Context(Executors.newSingleThreadExecutor(), new MockMetric()),
            null,
            null,
            slobrokApi,
            rootManager);


    @Test
    void testGetSlobrokEntries() throws Exception {
        testGetSlobrokEntriesWith("foo", "foo");
    }

    @Test
    void testGetSlobrokEntriesWithoutPattern() throws Exception {
        testGetSlobrokEntriesWith(null, InstanceRequestHandler.DEFAULT_SLOBROK_PATTERN);
    }

    @Test
    void testGetServiceStatusInfo() throws IOException {
        ServiceType serviceType = new ServiceType("serviceType");
        ConfigId configId = new ConfigId("configId");
        ServiceStatus serviceStatus = ServiceStatus.UP;
        when(rootManager.getStatus(APPLICATION_ID, CLUSTER_ID, serviceType, configId))
                .thenReturn(new ServiceStatusInfo(serviceStatus));


        String uriPath = String.format(
                "/orchestrator/v1/instances/%s/serviceStatusInfo?clusterId=%s&serviceType=%s&configId=%s",
                APPLICATION_INSTANCE_REFERENCE,
                CLUSTER_ID.s(),
                serviceType.s(),
                configId.s());
        ServiceStatusInfo serviceStatusInfo = executeRequest(uriPath, new TypeReference<>(){});

        ServiceStatus actualServiceStatus = serviceStatusInfo.serviceStatus();
        verify(rootManager).getStatus(APPLICATION_ID, CLUSTER_ID, serviceType, configId);
        assertEquals(serviceStatus, actualServiceStatus);
    }

    @Test
    void testBadRequest() {
        String uriPath = String.format(
                "/orchestrator/v1/instances/%s/serviceStatusInfo?clusterId=%s",
                APPLICATION_INSTANCE_REFERENCE,
                CLUSTER_ID.s());
        HttpRequest request = HttpRequest.createTestRequest("http://localhost" + uriPath, GET);
        HttpResponse response = handler.handle(request);
        assertEquals(400, response.getStatus());
    }

    private void testGetSlobrokEntriesWith(String pattern, String expectedLookupPattern)
            throws Exception{
        when(slobrokApi.lookup(APPLICATION_ID, expectedLookupPattern))
                .thenReturn(ENTRIES);

        String uriPath = String.format("/orchestrator/v1/instances/%s/slobrok", APPLICATION_INSTANCE_REFERENCE);
        if (pattern != null) {
            uriPath += "?pattern=" + pattern;
        }
        List<SlobrokEntryResponse> response = executeRequest(uriPath, new TypeReference<>() {});

        verify(slobrokApi).lookup(APPLICATION_ID, expectedLookupPattern);

        String actualJson = jsonMapper.writeValueAsString(response);
        assertEquals(
                "[{\"name\":\"name1\",\"spec\":\"tcp/spec:1\"},{\"name\":\"name2\",\"spec\":\"tcp/spec:2\"}]",
                actualJson);
    }

    private <T> T executeRequest(String path, TypeReference<T> responseEntityType) throws IOException {
        HttpRequest request = HttpRequest.createTestRequest("http://localhost" + path, GET);
        HttpResponse response = handler.handle(request);
        assertEquals(200, response.getStatus());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        return jsonMapper.readValue(out.toByteArray(), responseEntityType);
    }
}
