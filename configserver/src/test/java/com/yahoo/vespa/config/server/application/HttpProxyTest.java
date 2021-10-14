// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.HttpFetcher;
import com.yahoo.vespa.config.server.http.RequestTimeoutException;
import com.yahoo.vespa.config.server.http.StaticResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.vespa.config.server.application.MockModel.createServiceInfo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpProxyTest {

    private final HttpFetcher fetcher = mock(HttpFetcher.class);
    private final HttpProxy proxy = new HttpProxy(fetcher);

    private static final String hostname = "foo.yahoo.com";
    private static final int port = 19050;
    private final Application applicationMock = mock(Application.class);

    @Before
    public void setup() {
        Model modelMock = createClusterController();
        when(applicationMock.getModel()).thenReturn(modelMock);
    }

    @Test
    public void testNormalGet() throws Exception {
        ArgumentCaptor<HttpFetcher.Params> actualParams = ArgumentCaptor.forClass(HttpFetcher.Params.class);
        ArgumentCaptor<URL> actualUrl = ArgumentCaptor.forClass(URL.class);
        HttpResponse response = new StaticResponse(200, "application/json", "body");
        when(fetcher.get(actualParams.capture(), actualUrl.capture())).thenReturn(response);

        HttpResponse actualResponse = proxy.get(applicationMock, hostname, CLUSTERCONTROLLER_CONTAINER.serviceName,
                                                "clustercontroller-status/v1/clusterName");

        assertEquals(1, actualParams.getAllValues().size());
        assertEquals(2000, actualParams.getValue().readTimeoutMs);

        assertEquals(1, actualUrl.getAllValues().size());
        assertEquals(new URL("http://" + hostname + ":" + port + "/clustercontroller-status/v1/clusterName"),
                actualUrl.getValue());

        // The HttpResponse returned by the fetcher IS the same object as the one returned by the proxy,
        // when everything goes well.
        assertSame(actualResponse, response);
    }

    @Test(expected = RequestTimeoutException.class)
    public void testFetchException() {
        when(fetcher.get(any(), any())).thenThrow(new RequestTimeoutException("timed out"));

        proxy.get(applicationMock, hostname, CLUSTERCONTROLLER_CONTAINER.serviceName,
                  "clustercontroller-status/v1/clusterName");
    }

    private static MockModel createClusterController() {
        ServiceInfo container = createServiceInfo(
                hostname,
                "foo", // name
                CLUSTERCONTROLLER_CONTAINER.serviceName,
                ClusterSpec.Type.container,
                port,
                "state http external query");
        ServiceInfo serviceNoStatePort = createServiceInfo(hostname, "storagenode", "storagenode",
                                                           ClusterSpec.Type.content, 1234, "rpc");
        HostInfo hostInfo = new HostInfo(hostname, Arrays.asList(container, serviceNoStatePort));

        return new MockModel(Collections.singleton(hostInfo));
    }

}
