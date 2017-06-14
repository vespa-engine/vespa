// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handler;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class ResponseTestCase {

    @Test
    public void requireThatCharsetParsingWorks() {
        assertThat(Response.charset("text/foobar").toString().toLowerCase(), equalTo("utf-8"));
        assertThat(Response.charset("adsf").toString().toLowerCase(), equalTo("utf-8"));
        assertThat(Response.charset("").toString().toLowerCase(), equalTo("utf-8"));
        assertThat(Response.charset(null).toString().toLowerCase(), equalTo("utf-8"));

        assertThat(Response.charset("something; charset=US-ASCII").toString().toLowerCase(), equalTo("us-ascii"));
        assertThat(Response.charset("something; charset=iso-8859-1").toString().toLowerCase(), equalTo("iso-8859-1"));

        assertThat(Response.charset("something; charset=").toString().toLowerCase(), equalTo("utf-8"));
        assertThat(Response.charset("something; charset=bananarama").toString().toLowerCase(), equalTo("utf-8"));
    }

    @Test
    public void testDefaultResponseBody() {
        Response res1 = new Response();
        Response res2 = new Response(new byte[0]);

        assertThat(res1.getBody(), notNullValue());
        assertThat(res1.getBody().length, is(0));
        assertThat(res2.getBody(), notNullValue());
        assertThat(res2.getBody().length, is(0));
    }
}
