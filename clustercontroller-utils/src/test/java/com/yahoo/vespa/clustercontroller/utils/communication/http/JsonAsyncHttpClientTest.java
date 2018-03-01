// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonAsyncHttpClientTest {

    @Test
    public void testJSONInJSONOut() throws Exception {
        DummyAsyncHttpClient dummy = new DummyAsyncHttpClient(
                new HttpResult().setContent(new JSONObject().put("bar", 42)));
        JsonAsyncHttpClient client = new JsonAsyncHttpClient(dummy);
        client.addJsonContentType(true);
        client.verifyRequestContentAsJson(true);

        HttpRequest r = new HttpRequest();
        r.setPostContent(new JSONObject().put("foo", 34));

        AsyncOperation<JsonHttpResult> result = client.execute(r);

        assertEquals(new JSONObject().put("bar", 42).toString(), result.getResult().getJson().toString());
        assertTrue(result.isSuccess());

        result.toString();
        client.close();
    }

    @Test
    public void testStringInJSONOut() throws Exception {
        DummyAsyncHttpClient dummy = new DummyAsyncHttpClient(
                new HttpResult().setContent(new JSONObject().put("bar", 42).toString()));
        JsonAsyncHttpClient client = new JsonAsyncHttpClient(dummy);

        HttpRequest r = new HttpRequest();
        r.setPostContent(new JSONObject().put("foo", 34).toString());

        AsyncOperation<JsonHttpResult> result = client.execute(r);

        assertEquals(new JSONObject().put("bar", 42).toString(), result.getResult().getJson().toString());
    }

    @Test
    public void testIllegalJsonIn() throws Exception {
        DummyAsyncHttpClient dummy = new DummyAsyncHttpClient(
                new HttpResult().setContent(new JSONObject().put("bar", 42)));
        JsonAsyncHttpClient client = new JsonAsyncHttpClient(dummy);

        try {
            HttpRequest r = new HttpRequest();
            r.setPostContent("my illegal json");

            client.execute(r);
            assertTrue(false);
        } catch (Exception e) {

        }
    }

    @Test
    public void testIllegalJSONOut() throws Exception {
        DummyAsyncHttpClient dummy = new DummyAsyncHttpClient(
                new HttpResult().setContent("my illegal json"));
        JsonAsyncHttpClient client = new JsonAsyncHttpClient(dummy);

        HttpRequest r = new HttpRequest();
        r.setPostContent(new JSONObject().put("foo", 34).toString());

        AsyncOperation<JsonHttpResult> result = client.execute(r);

        assertEquals("{\"error\":\"Invalid JSON in output: A JSONObject text must begin with '{' at character 1 of my illegal json\",\"output\":\"my illegal json\"}", result.getResult().getJson().toString());
    }

    @Test
    public void testEmptyReply() {
        class Client implements AsyncHttpClient<HttpResult> {
            AsyncOperationImpl<HttpResult> lastOp;
            @Override
            public AsyncOperation<HttpResult> execute(HttpRequest r) {
                return lastOp = new AsyncOperationImpl<>(r.toString());
            }
            @Override
            public void close() {
            }
        };
        Client client = new Client();
        JsonAsyncHttpClient jsonClient = new JsonAsyncHttpClient(client);
        AsyncOperation<JsonHttpResult> op = jsonClient.execute(new HttpRequest());
        client.lastOp.setResult(null);
        assertNull(op.getResult());
    }

    @Test
    public void testNotVerifyingJson() throws Exception {
        DummyAsyncHttpClient dummy = new DummyAsyncHttpClient(
                new HttpResult().setContent(new JSONObject().put("bar", 42)));
        JsonAsyncHttpClient client = new JsonAsyncHttpClient(dummy);
        client.addJsonContentType(true);
        client.verifyRequestContentAsJson(false);

        HttpRequest r = new HttpRequest();
        r.setPostContent(new JSONObject().put("foo", 34));

        AsyncOperation<JsonHttpResult> result = client.execute(r);

        assertEquals(new JSONObject().put("bar", 42).toString(), result.getResult().getJson().toString());
        assertTrue(result.isSuccess());

        result.toString();
        client.close();
    }

}
