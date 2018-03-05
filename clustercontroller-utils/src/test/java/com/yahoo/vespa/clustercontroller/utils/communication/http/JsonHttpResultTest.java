// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonHttpResultTest {

    @Test
    public void testCopyConstructor() {
        assertEquals("{}", new JsonHttpResult(new HttpResult()).getJson().toString());
    }

    @Test
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

    @Test
    public void testNonJsonOutput() {
        JsonHttpResult result = new JsonHttpResult();
        result.setContent("Foo");
        StringBuilder sb = new StringBuilder();
        result.printContent(sb);
        assertEquals("Foo", sb.toString());
    }

    @Test
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
