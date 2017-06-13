// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.yahoo.search.federation.ProviderConfig;
import org.junit.Test;

import static com.yahoo.search.federation.ProviderConfig.Yca;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class HttpParametersTest {

    @Test
    public void create_from_config() throws Exception {
        ProviderConfig config = new ProviderConfig(new ProviderConfig.Builder()
                .connectionTimeout(1.0)
                .maxConnectionPerRoute(2)
                .maxConnections(3)
                .path("myPath")
                .readTimeout(4)
                .socketBufferBytes(5)
                .yca(new Yca.Builder()
                        .applicationId("myId")
                        .host("myYcaHost")
                        .port(7)
                        .retry(8)
                        .ttl(9)
                        .useProxy(true)));

        HTTPParameters httpParameters = new HTTPParameters(config);

        // Written to configuredConnectionTimeout, but it is not accessible!?
        //assertThat(httpParameters.getConnectionTimeout(), is(1000));


        // This value is not set from config by the constructor!?
        //assertThat(httpParameters.getMaxConnectionsPerRoute(), is(2));

        // This value is not set from config by the constructor!?
        //assertThat(httpParameters.getMaxTotalConnections(), is(3));

        assertThat(httpParameters.getPath(), is("/myPath"));

        // This value is not set from config by the constructor!?
        //assertThat(httpParameters.getReadTimeout(), is(4));

        // This value is not set from config by the constructor!?
        //assertThat(httpParameters.getSocketBufferSizeBytes(), is(5));


        assertThat(httpParameters.getYcaUseProxy(), is(true));
        assertThat(httpParameters.getYcaApplicationId(), is("myId"));
        assertThat(httpParameters.getYcaProxy(), is("myYcaHost"));
        assertThat(httpParameters.getYcaPort(), is(7));
        assertThat(httpParameters.getYcaRetry(), is(8000L));
        assertThat(httpParameters.getYcaTtl(), is(9000L));
    }

    @Test
    public void requireFreezeWorksForAccessors() {
        HTTPParameters p = new HTTPParameters();
        boolean caught = false;
        final int expected = 37;
        p.setConnectionTimeout(expected);
        assertEquals(expected, p.getConnectionTimeout());
        p.freeze();
        try {
            p.setConnectionTimeout(0);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setReadTimeout(expected);
        assertEquals(expected, p.getReadTimeout());
        p.freeze();
        try {
            p.setReadTimeout(0);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setPersistentConnections(true);
        assertTrue(p.getPersistentConnections());
        p.freeze();
        try {
            p.setPersistentConnections(false);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        assertEquals("http", p.getProxyType());

        p = new HTTPParameters();
        caught = false;
        p.setEnableProxy(true);
        assertTrue(p.getEnableProxy());
        p.freeze();
        try {
            p.setEnableProxy(false);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setProxyHost("nalle");
        assertEquals("nalle", p.getProxyHost());
        p.freeze();
        try {
            p.setProxyHost("jappe");
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setProxyPort(expected);
        assertEquals(expected, p.getProxyPort());
        p.freeze();
        try {
            p.setProxyPort(0);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setMethod("POST");
        assertEquals("POST", p.getMethod());
        p.freeze();
        try {
            p.setMethod("GET");
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setSchema("gopher");
        assertEquals("gopher", p.getSchema());
        p.freeze();
        try {
            p.setSchema("http");
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setInputEncoding("iso-8859-15");
        assertEquals("iso-8859-15", p.getInputEncoding());
        p.freeze();
        try {
            p.setInputEncoding("shift-jis");
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setOutputEncoding("iso-8859-15");
        assertEquals("iso-8859-15", p.getOutputEncoding());
        p.freeze();
        try {
            p.setOutputEncoding("shift-jis");
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setMaxTotalConnections(expected);
        assertEquals(expected, p.getMaxTotalConnections());
        p.freeze();
        try {
            p.setMaxTotalConnections(0);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setMaxConnectionsPerRoute(expected);
        assertEquals(expected, p.getMaxConnectionsPerRoute());
        p.freeze();
        try {
            p.setMaxConnectionsPerRoute(0);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setSocketBufferSizeBytes(expected);
        assertEquals(expected, p.getSocketBufferSizeBytes());
        p.freeze();
        try {
            p.setSocketBufferSizeBytes(0);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);

        p = new HTTPParameters();
        caught = false;
        p.setRetries(expected);
        assertEquals(expected, p.getRetries());
        p.freeze();
        try {
            p.setRetries(0);
        } catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);
    }
}
