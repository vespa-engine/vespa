// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProxyAsyncHttpClientTest {

    @Test
    public void testSimple() throws Exception {
        // Can't really test much here, but verifies that the code runs.
        DummyAsyncHttpClient dummy = new DummyAsyncHttpClient(
                new HttpResult().setContent(new JSONObject().put("bar", 42)));
        ProxyAsyncHttpClient client = new ProxyAsyncHttpClient<>(dummy, "myproxyhost", 1234);

        HttpRequest r = new HttpRequest();
        r.setPath("/foo");
        r.setHost("myhost");
        r.setPort(4567);

        r.setPostContent(new JSONObject().put("foo", 34));

        client.execute(r);

        assertEquals(new HttpRequest().setPath("/myhost:4567/foo")
                                      .setHost("myproxyhost")
                                      .setPort(1234)
                                      .setPostContent(new JSONObject().put("foo", 34)),
                     dummy.lastRequest);
    }

    @Test
    public void testNoAndEmptyPath() throws Exception {
        DummyAsyncHttpClient dummy = new DummyAsyncHttpClient(
                new HttpResult().setContent(new JSONObject().put("bar", 42)));
        ProxyAsyncHttpClient client = new ProxyAsyncHttpClient<>(dummy, "myproxyhost", 1234);
        try{
            client.execute(new HttpRequest());
            assertTrue(false);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Host and path must be set prior"));
        }
        client.execute(new HttpRequest().setHost("local").setPath(""));
    }

}
