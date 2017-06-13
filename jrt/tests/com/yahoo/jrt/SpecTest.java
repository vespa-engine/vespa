// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.net.InetSocketAddress;

public class SpecTest extends junit.framework.TestCase {

    public SpecTest(String name) {
        super(name);
    }

    public void testPort() {
        Spec              spec = new Spec(457);
        InetSocketAddress addr = new InetSocketAddress(457);

        assertEquals("tcp/457", spec.toString());
        assertFalse(spec.malformed());
        assertEquals(457, spec.port());
        assertNull(spec.host());
        assertTrue(addr.equals(spec.address()));
    }

    public void testHostPort() {
        String            host = "localhost";
        Spec              spec = new Spec(host, 457);
        InetSocketAddress addr = new InetSocketAddress(host, 457);

        assertEquals("tcp/localhost:457", spec.toString());
        assertFalse(spec.malformed());
        assertEquals(457, spec.port());
        assertEquals(host, spec.host());
        assertTrue(addr.equals(spec.address()));
    }

    public void testBogusHostPort() {
        String            host = "bogus.host.name";
        Spec              spec = new Spec(host, 457);
        InetSocketAddress addr = new InetSocketAddress(host, 457);

        assertEquals("tcp/bogus.host.name:457", spec.toString());
        assertFalse(spec.malformed());
        assertEquals(457, spec.port());
        assertEquals(host, spec.host());
        assertTrue(addr.equals(spec.address()));
    }

    public void testSpec1() {
        Spec              spec = new Spec("tcp/localhost:8080");
        InetSocketAddress addr = new InetSocketAddress("localhost", 8080);

        assertEquals("tcp/localhost:8080", spec.toString());
        assertFalse(spec.malformed());
        assertEquals(8080, spec.port());
        assertEquals("localhost", spec.host());
        assertTrue(addr.equals(spec.address()));
    }

    public void testSpec2() {
        Spec              spec = new Spec("tcp/8080");
        InetSocketAddress addr = new InetSocketAddress(8080);

        assertEquals("tcp/8080", spec.toString());
        assertFalse(spec.malformed());
        assertEquals(8080, spec.port());
        assertNull(spec.host());
        assertTrue(addr.equals(spec.address()));
    }

    public void testBogusSpec1() {
        Spec spec = new Spec("localhost:8080");

        assertEquals("MALFORMED", spec.toString());
        assertTrue(spec.malformed());
        assertEquals(0, spec.port());
        assertNull(spec.host());
        assertNull(spec.address());
    }

    public void testBogusSpec2() {
        Spec spec = new Spec("tcp/localhost:xyz");

        assertEquals("MALFORMED", spec.toString());
        assertTrue(spec.malformed());
        assertEquals(0, spec.port());
        assertNull(spec.host());
        assertNull(spec.address());
    }

    public void testBogusSpec3() {
        Spec spec = new Spec("tcp/localhost:");

        assertEquals("MALFORMED", spec.toString());
        assertTrue(spec.malformed());
        assertEquals(0, spec.port());
        assertNull(spec.host());
        assertNull(spec.address());
    }
}
