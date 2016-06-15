// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client.filter.core;

import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Request;
import com.ning.http.client.filter.FilterContext;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.client.filter.ResponseFilterContext;
import org.testng.annotations.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class ResponseFilterBridgeTestCase {

    @Test(enabled = false)
    public void requireThatResponseFilterBridgeConvertsFieldsProperly() throws MalformedURLException, URISyntaxException {
        ResponseFilterContext responseFilterContext = ResponseFilterBridge.toResponseFilterContext(
                                                                                                constructFilterContext(),
                                                                                                constructRequest()
        );

        assertEquals("http://localhost:8080/echo", responseFilterContext.getRequestURI().toString());
        assertEquals(200, responseFilterContext.getResponseStatusCode());
        assertEquals("v1", responseFilterContext.getResponseFirstHeader("k1"));
        assertEquals("v2", responseFilterContext.getResponseFirstHeader("k2"));
        Map<String, Object> customParams = responseFilterContext.getRequestContext();
        assertEquals("cv1", customParams.get("c1"));
        assertEquals("cv2", customParams.get("c2"));

    }

    private HttpRequest constructRequest() {
        HttpRequest request = mock(HttpRequest.class);
        Map<String, Object> customParams = new HashMap<>();
        customParams.put("c1", "cv1");
        customParams.put("c2", "cv2");
        when(request.context()).thenReturn(customParams);
        return request;
    }

    private FilterContext<?> constructFilterContext() throws MalformedURLException, URISyntaxException {
        FilterContext.FilterContextBuilder<?> builder = new FilterContext.FilterContextBuilder<>();

        Request request = mock(Request.class);
        URL url = new URL("http://localhost:8080/echo");
        URI reqURI = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(),
                url.getQuery(), url.getRef());
        when(request.getURI()).thenReturn(reqURI);

        HttpResponseStatus responseStatus = mock(HttpResponseStatus.class);
        when(responseStatus.getStatusCode()).thenReturn(200);

        HttpResponseHeaders responseHeaders = mock(HttpResponseHeaders.class);
        FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap();
        headers.add("k1", "v1", "v12", "v13");
        headers.add("k2", "v2");
        when(responseHeaders.getHeaders()).thenReturn(headers);

        builder.request(request);
        builder.responseStatus(responseStatus);
        builder.responseHeaders(responseHeaders);

        return builder.build();
    }
}
