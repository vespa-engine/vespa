// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author lesters
 */
public class UrlNodeTest {

    @Test
    public void testSetValue() {
        UrlNode url = new UrlNode();
        assertThat(url.toString(), is("(null)"));

        url = new UrlNode(new UrlReference("https://docs.vespa.ai/"));
        assertThat(url.getUrlReference().value(), is("https://docs.vespa.ai/"));

        url = new UrlNode(new UrlReference("pom.xml"));
        assertThat(url.getValue(), is("pom.xml"));
    }

}
