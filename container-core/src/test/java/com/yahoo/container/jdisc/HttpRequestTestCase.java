// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.text.Utf8;

/**
 * API control of HttpRequest.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class HttpRequestTestCase {
    private static final String X_RAY_YANKEE_ZULU = "x-ray yankee zulu";
    private static final String HTTP_MAILHOST_25_ALPHA_BRAVO_CHARLIE_DELTA = "http://mailhost:25/alpha?bravo=charlie&falseboolean=false&trueboolean=true";
    HttpRequest r;
    InputStream requestData;

    @BeforeEach
    public void setUp() throws Exception {
        requestData = new ByteArrayInputStream(Utf8.toBytes(X_RAY_YANKEE_ZULU));
        r = HttpRequest.createTestRequest(HTTP_MAILHOST_25_ALPHA_BRAVO_CHARLIE_DELTA, Method.GET, requestData, Collections.singletonMap("foxtrot", "golf"));
    }

    @AfterEach
    public void tearDown() throws Exception {
        r = null;
    }

    @Test
    final void testGetMethod() {
        assertSame(Method.GET, r.getMethod());
    }

    @Test
    final void testGetUri() throws URISyntaxException {
        assertEquals(new URI(HTTP_MAILHOST_25_ALPHA_BRAVO_CHARLIE_DELTA), r.getUri());
    }

    @Test
    final void testGetJDiscRequest() throws URISyntaxException {
        assertEquals(new URI(HTTP_MAILHOST_25_ALPHA_BRAVO_CHARLIE_DELTA), r.getJDiscRequest().getUri());
    }

    @Test
    final void testGetProperty() {
        assertEquals("charlie", r.getProperty("bravo"));
        assertEquals("golf", r.getProperty("foxtrot"));
        assertNull(r.getProperty("zulu"));
    }

    @Test
    final void testPropertyMap() {
        assertEquals(4, r.propertyMap().size());
    }

    @Test
    final void testGetBooleanProperty() {
        assertTrue(r.getBooleanProperty("trueboolean"));
        assertFalse(r.getBooleanProperty("falseboolean"));
        assertFalse(r.getBooleanProperty("bravo"));
    }

    @Test
    final void testHasProperty() {
        assertFalse(r.hasProperty("alpha"));
        assertTrue(r.hasProperty("bravo"));
    }

    @Test
    final void testGetHeader() {
        assertNull(r.getHeader("SyntheticHeaderFor-com.yahoo.container.jdisc.HttpRequestTestCase"));
    }

    @Test
    final void testGetHost() {
        assertEquals("mailhost", r.getHost());
    }

    @Test
    final void testGetPort() {
        assertEquals(25, r.getPort());
    }

    @Test
    final void testGetData() throws IOException {
        byte[] b = new byte[X_RAY_YANKEE_ZULU.length()];
        r.getData().read(b);
        assertEquals(X_RAY_YANKEE_ZULU, Utf8.toString(b));
    }

}
