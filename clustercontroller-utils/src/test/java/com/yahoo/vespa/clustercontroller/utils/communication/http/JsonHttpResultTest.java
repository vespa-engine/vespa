// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonHttpResultTest {

    @Test
    void testCopyConstructor() {
        assertEquals("{}", new JsonHttpResult(new HttpResult()).getJson().toString());
    }

    @Test
    void testOutput() {
        assertEquals("""
                     HTTP 200/OK

                     JSON: {
                       "foo" : 3
                     }""",
                     new JsonHttpResult(new HttpResult().setContent("{ \"foo\" : 3 }")).toString(true));
        assertEquals("""
                     HTTP 200/OK

                     { "foo" : }""",
                     new JsonHttpResult(new HttpResult().setContent("{ \"foo\" : }")).toString(true));
    }

    @Test
    void testNonJsonOutput() {
        JsonHttpResult result = new JsonHttpResult();
        result.setContent("Foo");
        StringBuilder sb = new StringBuilder();
        result.printContent(sb);
        assertEquals("Foo", sb.toString());
    }

}
