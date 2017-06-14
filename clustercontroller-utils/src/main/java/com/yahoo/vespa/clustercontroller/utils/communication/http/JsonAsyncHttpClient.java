// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.RedirectedAsyncOperation;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Wrapped for the HTTP client, converting requests to/from JSON.
 */
public class JsonAsyncHttpClient implements AsyncHttpClient<JsonHttpResult> {
    private AsyncHttpClient<HttpResult> client;
    private boolean verifyRequestContentAsJson = true;
    private boolean addJsonContentType = true;

    public JsonAsyncHttpClient(AsyncHttpClient<HttpResult> client) {
        this.client = client;
    }

    public JsonAsyncHttpClient verifyRequestContentAsJson(boolean doIt) {
        verifyRequestContentAsJson = doIt;
        return this;
    }

    public JsonAsyncHttpClient addJsonContentType(boolean doIt) {
        addJsonContentType = doIt;
        return this;
    }

    public AsyncOperation<JsonHttpResult> execute(HttpRequest r) {
        if (verifyRequestContentAsJson) {
            if (r.getPostContent() != null && !(r.getPostContent() instanceof JSONObject)) {
                try{
                    r = r.clone().setPostContent(new JSONObject(r.getPostContent().toString()));
                } catch (JSONException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        if (addJsonContentType && r.getPostContent() != null) {
            r = r.clone().addHttpHeader("Content-Type", "application/json");
        }
        final AsyncOperation<HttpResult> op = client.execute(r);
        return new RedirectedAsyncOperation<HttpResult, JsonHttpResult>(op) {
            @Override
            public JsonHttpResult getResult() {
                return (op.getResult() == null ? null : new JsonHttpResult(op.getResult()));
            }
        };
    }

    @Override
    public void close() {
        client.close();
    }
}
