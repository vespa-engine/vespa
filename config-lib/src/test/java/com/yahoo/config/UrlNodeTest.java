// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 */
public class UrlNodeTest {

    @Test
    public void testSetValue() {
        UrlNode url = new UrlNode();
        assertEquals("(null)", url.toString());

        url = new UrlNode(new UrlReference("https://docs.vespa.ai/"));
        assertEquals("https://docs.vespa.ai/", url.getUrlReference().value());

        url = new UrlNode(new UrlReference("pom.xml"));
        assertEquals("pom.xml", url.getValue());
    }

}
