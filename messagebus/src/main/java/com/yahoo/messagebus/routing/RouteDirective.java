// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * This class represents a route directive within a {@link Hop}'s selector. This will be replaced by the named route
 * when evaluated. If the route is not present in the running protocol's routing table, routing will fail.
 *
 * @author Simon Thoresen Hult
 */
public class RouteDirective implements HopDirective {

    private final String name;

    /**
     * Constructs a new directive to insert a route.
     *
     * @param name The name of the route to insert.
     */
    public RouteDirective(String name) {
        this.name = name;
    }

    @Override
    public boolean matches(HopDirective dir) {
        return dir instanceof RouteDirective && name.equals(((RouteDirective)dir).name);
    }

    /**
     * Returns the name of the route to insert.
     *
     * @return The route name.
     */
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RouteDirective)) {
            return false;
        }
        RouteDirective rhs = (RouteDirective)obj;
        if (!name.equals(rhs.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "route:" + name;
    }

    @Override
    public String toDebugString() {
        return "RouteDirective(name = '" + name + "')";
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
