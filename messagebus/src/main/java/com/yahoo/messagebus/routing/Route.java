// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>A route is a list of {@link Hop hops} that are resolved from first to last
 * as a routable moves from source to destination. A route may be changed at any
 * time be either application logic or an invoked {@link RoutingPolicy}, so no
 * guarantees on actual path can be given without the full knowledge of all that
 * logic.</p>
 *
 * <p>To construct a route you may either use the factory method {@link
 * #parse(String)} to produce a route instance from a string representation, or
 * you may build one programatically through the hop accessors.</p>
 *
 * @author bratseth
 * @author Simon Thoresen Hult
 */
public class Route {

    private final List<Hop> hops = new ArrayList<Hop>();
    private String cache = null;

    /**
     * <p>Creates an empty route that contains no hops.</p>
     */
    public Route() {
        // empty
    }

    /**
     * <p>The copy constructor ignores integrity, it simply duplicates the list
     * of hops in the other route. If that route is illegal, then so is
     * this.</p>
     *
     * @param route The route to copy.
     */
    public Route(Route route) {
        this(route.hops);
        cache = route.cache;
    }

    private void setRaw(String s) {
        cache = s;
    }

    /**
     * <p>Parses the given string as a list of space-separated hops. The {@link
     * #toString()} method is compatible with this parser.</p>
     *
     * @param str The string to parse.
     * @return A route that corresponds to the string.
     */
    public static Route parse(String str) {
        if (str == null || str.length() == 0) {
            return new Route().addHop(new Hop().addDirective(new ErrorDirective("Failed to parse empty string.")));
        }
        RouteParser parser = new RouteParser(str);
        Route route = parser.route();
        route.setRaw(str);
        return route;
    }

    /**
     * <p>Constructs a route based on a list of hops.</p>
     *
     * @param hops The hops to be contained in this.
     */
    public Route(List<Hop> hops) {
        this.hops.addAll(hops);
    }

    /**
     * <p>Returns whether or not there are any hops in this route.</p>
     *
     * @return True if there is at least one hop.
     */
    public boolean hasHops() {
        return !hops.isEmpty();
    }

    /**
     * <p>Returns the number of hops that make up this route.</p>
     *
     * @return The number of hops.
     */
    public int getNumHops() {
        return hops.size();
    }

    /**
     * <p>Returns the hop at the given index.</p>
     *
     * @param i The index of the hop to return.
     * @return The hop.
     */
    public Hop getHop(int i) {
        return hops.get(i);
    }

    /**
     * <p>Adds a hop to the list of hops that make up this route.</p>
     *
     * @param hop The hop to add.
     * @return This, to allow chaining.
     */
    public Route addHop(Hop hop) {
        cache = null;
        hops.add(hop);
        return this;
    }

    /**
     * <p>Sets the hop at a given index.</p>
     *
     * @param i   The index at which to set the hop.
     * @param hop The hop to set.
     * @return This, to allow chaining.
     */
    public Route setHop(int i, Hop hop) {
        cache = null;
        hops.set(i, hop);
        return this;
    }

    /**
     * <p>Removes the hop at a given index.</p>
     *
     * @param i The index of the hop to remove.
     * @return The hop removed.
     */
    public Hop removeHop(int i) {
        cache = null;
        return hops.remove(i);
    }

    /**
     * <p>Clears the list of hops that make up this route.</p>
     *
     * @return This, to allow chaining.
     */
    public Route clearHops() {
        cache = null;
        hops.clear();
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Route)) {
            return false;
        }
        Route rhs = (Route)obj;
        if (hops.size() != rhs.hops.size()) {
            return false;
        }
        for (int i = 0; i < hops.size(); ++i) {
            if (!hops.get(i).equals(rhs.hops.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        if (cache == null) {
            StringBuilder ret = new StringBuilder("");
            for (int i = 0; i < hops.size(); ++i) {
                ret.append(hops.get(i));
                if (i < hops.size() - 1) {
                    ret.append(" ");
                }
            }
            cache = ret.toString();
        }
        return cache;
    }

    /**
     * <p>Returns a string representation of this that can be debugged but not
     * parsed.</p>
     *
     * @return The debug string.
     */
    public String toDebugString() {
        StringBuilder ret = new StringBuilder("Route(hops = { ");
        for (int i = 0; i < hops.size(); ++i) {
            ret.append(hops.get(i).toDebugString());
            if (i < hops.size() - 1) {
                ret.append(", ");
            }
        }
        ret.append(" })");
        return ret.toString();
    }

    @Override
    public int hashCode() {
        int result = hops != null ? hops.hashCode() : 0;
        result = 31 * result + (cache != null ? cache.hashCode() : 0);
        return result;
    }
}
