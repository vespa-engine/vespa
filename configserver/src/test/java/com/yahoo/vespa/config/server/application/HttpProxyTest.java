// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.Model;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.HttpFetcher;
import com.yahoo.vespa.config.server.http.StaticResponse;
import com.yahoo.vespa.config.server.http.RequestTimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.URL;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
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
        Model modelMock = MockModel.createClusterController(hostname, port);
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
        assertTrue(actualResponse == response);
    }

    @Test(expected = RequestTimeoutException.class)
    public void testFetchException() {
        when(fetcher.get(any(), any())).thenThrow(new RequestTimeoutException("timed out"));

        HttpResponse actualResponse = proxy.get(applicationMock, hostname, CLUSTERCONTROLLER_CONTAINER.serviceName,
                                                "clustercontroller-status/v1/clusterName");
    }
}
