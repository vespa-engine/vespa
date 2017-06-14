// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import junit.framework.TestCase;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class JsonHttpResultTest extends TestCase {

    public void testCopyConstructor() {
        assertEquals("{}", new JsonHttpResult(new HttpResult()).getJson().toString());
    }

    public void testOutput() {
        assertEquals("HTTP 200/OK\n"
                   + "\n"
                   + "JSON: {\"foo\": 3}",
                     new JsonHttpResult(new HttpResult().setContent("{ \"foo\" : 3 }")).toString(true));
        assertEquals("HTTP 200/OK\n"
                + "\n"
                + "{ \"foo\" : }",
                new JsonHttpResult(new HttpResult().setContent("{ \"foo\" : }")).toString(true));
    }

    public void testNonJsonOutput() {
        JsonHttpResult result = new JsonHttpResult();
        result.setContent("Foo");
        StringBuilder sb = new StringBuilder();
        result.printContent(sb);
        assertEquals("Foo", sb.toString());
    }

    public void testInvalidJsonOutput() {
        JsonHttpResult result = new JsonHttpResult();
        result.setJson(new JSONObject() {
            @Override
            public String toString(int indent) throws JSONException {
                throw new JSONException("Foo");
            }
        });
        StringBuilder sb = new StringBuilder();
        result.printContent(sb);
        assertEquals("JSON: {}", sb.toString());
    }
}
