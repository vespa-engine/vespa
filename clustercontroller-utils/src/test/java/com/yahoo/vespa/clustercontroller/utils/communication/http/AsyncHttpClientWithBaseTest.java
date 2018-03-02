// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;
import org.junit.Test;

import static org.junit.Assert.*;

public class AsyncHttpClientWithBaseTest {

    @Test
    public void testOverride() {
        class HttpClient implements AsyncHttpClient<HttpResult> {
            HttpRequest lastRequest;
            @Override
            public AsyncOperation<HttpResult> execute(HttpRequest r) {
                lastRequest = r;
                return new AsyncOperationImpl<>("test");
            }
            @Override
            public void close() {
            }
        }

        HttpClient client = new HttpClient();
        AsyncHttpClientWithBase<HttpResult> base = new AsyncHttpClientWithBase<>(client);
            // No override by default
        HttpRequest r = new HttpRequest().setPath("/foo").setHost("bar").setPort(50);
        base.execute(r);
        assertEquals(client.lastRequest, r);
            // Base request always set
        base.setHttpRequestBase(null);
        base.execute(r);
        assertEquals(client.lastRequest, r);
            // Set an override
        base.setHttpRequestBase(new HttpRequest().setHttpOperation(HttpRequest.HttpOp.DELETE));
        base.execute(r);
        assertNotSame(client.lastRequest, r);
        assertEquals(HttpRequest.HttpOp.DELETE, client.lastRequest.getHttpOperation());

        base.close();
    }

    @Test
    public void testClientMustBeSet() {
        try{
            new AsyncHttpClientWithBase<>(null);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

}
