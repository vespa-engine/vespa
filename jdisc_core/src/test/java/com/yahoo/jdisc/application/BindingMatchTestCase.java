// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;


/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class BindingMatchTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Object obj = new Object();
        BindingMatch<Object> match = new BindingMatch<>(
                new UriPattern("http://*/*").match(URI.create("http://localhost:69/status.html")),
                obj);
        assertSame(obj, match.target());
        assertEquals(3, match.groupCount());
        assertEquals("localhost", match.group(0));
        assertEquals("69", match.group(1));
        assertEquals("status.html", match.group(2));
    }

    @Test
    public void requireThatConstructorArgumentsCanNotBeNull() {
        try {
            new BindingMatch<>(null, null);
            fail();
        } catch (NullPointerException e) {

        }
        try {
            new BindingMatch<>(new UriPattern("http://*/*").match(URI.create("http://localhost/")), null);
            fail();
        } catch (NullPointerException e) {

        }
        try {
            new BindingMatch<>(null, new Object());
            fail();
        } catch (NullPointerException e) {

        }
    }
}
