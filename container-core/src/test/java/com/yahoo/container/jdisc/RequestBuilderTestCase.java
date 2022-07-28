// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.jdisc.http.HttpRequest.Method;

/**
 * API check for HttpRequest.Builder.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class RequestBuilderTestCase {
    HttpRequest.Builder b;

    @BeforeEach
    public void setUp() throws Exception {
        HttpRequest r = HttpRequest.createTestRequest("http://ssh:22/alpha?bravo=charlie", Method.GET);
        b = new HttpRequest.Builder(r);
    }

    @AfterEach
    public void tearDown() throws Exception {
        b = null;
    }

    @Test
    final void testBasic() {
        HttpRequest r = b.put("delta", "echo").createDirectRequest();
        assertEquals("charlie", r.getProperty("bravo"));
        assertEquals("echo", r.getProperty("delta"));
    }

    @Test
    void testRemove() {
        HttpRequest orig = b.put("delta", "echo").createDirectRequest();

        HttpRequest child = new HttpRequest.Builder(orig).removeProperty("delta").createDirectRequest();
        assertFalse(child.propertyMap().containsKey("delta"));
    }

}
