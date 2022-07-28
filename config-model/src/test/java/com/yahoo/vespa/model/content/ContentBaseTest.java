// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.messagebus.routing.RouteSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContentBaseTest {
    public static String getHosts() {
        return "<?xml version='1.0' encoding='utf-8' ?>" +
                "<hosts>  " +
                "  <host name='foo'>" +
                "    <alias>node0</alias>" +
                "  </host>" +
                "</hosts>";
    }

    static void assertRoute(RouteSpec r, String name, String... hops) {
        assertEquals(name, r.getName());
        assertEquals(hops.length, r.getNumHops());
        for(int i = 0; i < hops.length; i++) {
            assertEquals(hops[i], r.getHop(i));
        }
    }
}
