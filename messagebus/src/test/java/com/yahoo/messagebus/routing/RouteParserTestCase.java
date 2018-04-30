// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class RouteParserTestCase {

    @Test
    public void testHopParser() {
        Hop hop = Hop.parse("foo");
        assertNotNull(hop);
        assertEquals(1, hop.getNumDirectives());
        assertVerbatimDirective(hop.getDirective(0), "foo");

        assertNotNull(hop = Hop.parse("foo/bar"));
        assertEquals(2, hop.getNumDirectives());
        assertVerbatimDirective(hop.getDirective(0), "foo");
        assertVerbatimDirective(hop.getDirective(1), "bar");

        assertNotNull(hop = Hop.parse("tcp/foo:666/bar"));
        assertEquals(1, hop.getNumDirectives());
        assertTcpDirective(hop.getDirective(0), "foo", 666, "bar");

        assertNotNull(hop = Hop.parse("route:foo"));
        assertEquals(1, hop.getNumDirectives());
        assertRouteDirective(hop.getDirective(0), "foo");

        assertNotNull(hop = Hop.parse("[Extern:tcp/localhost:3619;foo/bar]"));
        assertEquals(1, hop.getNumDirectives());
        assertPolicyDirective(hop.getDirective(0), "Extern","tcp/localhost:3619;foo/bar");

        assertNotNull(hop = Hop.parse("[AND:foo bar]"));
        assertEquals(1, hop.getNumDirectives());
        assertPolicyDirective(hop.getDirective(0), "AND","foo bar");

        assertNotNull(hop = Hop.parse("[DocumentRouteSelector:raw:route[2]\n" +
                                      "route[0].name \"foo\"\n" +
                                      "route[0].selector \"testdoc\"\n" +
                                      "route[0].feed \"myfeed\"\n" +
                                      "route[1].name \"bar\"\n" +
                                      "route[1].selector \"other\"\n" +
                                      "route[1].feed \"myfeed\"\n" +
                                      "]"));
        assertEquals(1, hop.getNumDirectives());
        assertPolicyDirective(hop.getDirective(0), "DocumentRouteSelector",
                              "raw:route[2]\n" +
                              "route[0].name \"foo\"\n" +
                              "route[0].selector \"testdoc\"\n" +
                              "route[0].feed \"myfeed\"\n" +
                              "route[1].name \"bar\"\n" +
                              "route[1].selector \"other\"\n" +
                              "route[1].feed \"myfeed\"");

        assertNotNull(hop = Hop.parse("[DocumentRouteSelector:raw:route[1]\n" +
                                      "route[0].name \"docproc/cluster.foo\"\n" +
                                      "route[0].selector \"testdoc\"\n" +
                                      "route[0].feed \"myfeed\"" +
                                      "]"));
        assertEquals(1, hop.getNumDirectives());
        assertPolicyDirective(hop.getDirective(0), "DocumentRouteSelector",
                              "raw:route[1]\n" +
                              "route[0].name \"docproc/cluster.foo\"\n" +
                              "route[0].selector \"testdoc\"\n" +
                              "route[0].feed \"myfeed\"");
    }

    @Test
    public void testHopParserErrors() {
        assertError(Hop.parse(""), "Failed to parse empty string.");
        assertError(Hop.parse("[foo"), "Unterminated '[' in '[foo'");
        assertError(Hop.parse("foo/[bar]]"), "Unexpected token ']' in 'foo/[bar]]'");
        assertError(Hop.parse("foo bar"), "Failed to completely parse 'foo bar'.");
    }

    @Test
    public void testShortRoute() {
        Route shortRoute = Route.parse("c");
        assertNotNull(shortRoute);
        assertEquals(1, shortRoute.getNumHops());
        Hop hop = shortRoute.getHop(0);
        assertNotNull(hop);
        assertEquals(1, hop.getNumDirectives());
        assertVerbatimDirective(hop.getDirective(0), "c");
    }

    @Test
    public void testShortHops() {
        Route shortRoute = Route.parse("a b c");
        assertNotNull(shortRoute);
        assertEquals(3, shortRoute.getNumHops());
        Hop hop = shortRoute.getHop(0);
        assertNotNull(hop);
        assertEquals(1, hop.getNumDirectives());
        assertVerbatimDirective(hop.getDirective(0), "a");
    }

    @Test
    public void testRouteParser() {
        Route route = Route.parse("foo bar/baz");
        assertNotNull(route);
        assertEquals(2, route.getNumHops());
        Hop hop = route.getHop(0);
        assertNotNull(hop);
        assertEquals(1, hop.getNumDirectives());
        assertVerbatimDirective(hop.getDirective(0), "foo");
        assertNotNull(hop = route.getHop(1));
        assertEquals(2, hop.getNumDirectives());
        assertVerbatimDirective(hop.getDirective(0), "bar");
        assertVerbatimDirective(hop.getDirective(1), "baz");

        assertNotNull(route = Route.parse("[Extern:tcp/localhost:3633;itr/session] default"));
        assertEquals(2, route.getNumHops());
        assertNotNull(hop = route.getHop(0));
        assertEquals(1, hop.getNumDirectives());
        assertPolicyDirective(hop.getDirective(0), "Extern", "tcp/localhost:3633;itr/session");
        assertNotNull(hop = route.getHop(1));
        assertEquals(1, hop.getNumDirectives());
        assertVerbatimDirective(hop.getDirective(0), "default");
    }

    @Test
    public void testRouteParserErrors() {
        assertError(Route.parse(""), "Failed to parse empty string.");
        assertError(Route.parse("foo [bar"), "Unterminated '[' in '[bar'");
        assertError(Route.parse("foo bar/[baz]]"), "Unexpected token ']' in 'bar/[baz]]'");
    }

    private static void assertError(Route route, String msg) {
        assertNotNull(route);
        assertEquals(1, route.getNumHops());
        assertError(route.getHop(0), msg);
    }

    private static void assertError(Hop hop, String msg) {
        assertNotNull(hop);
        System.out.println(hop.toDebugString());
        assertEquals(1, hop.getNumDirectives());
        assertErrorDirective(hop.getDirective(0), msg);
    }

    private static void assertErrorDirective(HopDirective dir, String msg) {
        assertNotNull(dir);
        assertTrue(dir instanceof ErrorDirective);
        assertEquals(msg, ((ErrorDirective)dir).getMessage());
    }

    private static void assertPolicyDirective(HopDirective dir, String name, String param) {
        assertNotNull(dir);
        assertTrue(dir instanceof PolicyDirective);
        assertEquals(name, ((PolicyDirective)dir).getName());
        assertEquals(param, ((PolicyDirective)dir).getParam());
    }

    private static void assertRouteDirective(HopDirective dir, String name) {
        assertNotNull(dir);
        assertTrue(dir instanceof RouteDirective);
        assertEquals(name, ((RouteDirective)dir).getName());
    }

    private static void assertTcpDirective(HopDirective dir, String host, int port, String session) {
        assertNotNull(dir);
        assertTrue(dir instanceof TcpDirective);
        assertEquals(host, ((TcpDirective)dir).getHost());
        assertEquals(port, ((TcpDirective)dir).getPort());
        assertEquals(session, ((TcpDirective)dir).getSession());
    }

    private static void assertVerbatimDirective(HopDirective dir, String image) {
        assertNotNull(dir);
        assertTrue(dir instanceof VerbatimDirective);
        assertEquals(image, ((VerbatimDirective)dir).getImage());
    }

}
