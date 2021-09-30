// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * At any time there may only ever be zero or one routing table registered in message bus for each protocol. This class
 * contains a list of named hops and routes that may be used to substitute references to these during route resolving.
 *
 * @author Simon Thoresen Hult
 */
public class RoutingTable {

    private final Map<String, HopBlueprint> hops = new LinkedHashMap<>();
    private final Map<String, Route> routes = new LinkedHashMap<>();

    /**
     * Creates a new routing table based on a given specification. This also verifies the integrity of the table.
     *
     * @param spec The specification to use.
     */
    public RoutingTable(RoutingTableSpec spec) {
        for (int i = 0; i < spec.getNumHops(); ++i) {
            HopSpec hopSpec = spec.getHop(i);
            hops.put(hopSpec.getName(), new HopBlueprint(hopSpec));
        }
        for (int i = 0; i < spec.getNumRoutes(); ++i) {
            RouteSpec routeSpec = spec.getRoute(i);
            Route route = new Route();
            for (int j = 0; j < routeSpec.getNumHops(); ++j) {
                route.addHop(Hop.parse(routeSpec.getHop(j)));
            }
            routes.put(routeSpec.getName(), route);
        }
    }

    /**
     * Returns whether or not there are any hops in this routing table.
     *
     * @return True if there is at least one hop.
     */
    public boolean hasHops() {
        return !hops.isEmpty();
    }

    /**
     * Returns the number of hops that are contained in this.
     *
     * @return The number of hops.
     */
    public int getNumHops() {
        return hops.size();
    }

    /**
     * Returns an iterator for the hops of this table.
     *
     * @return An iterator.
     */
    public HopIterator getHopIterator() {
        return new HopIterator(hops);
    }

    /**
     * Returns an iterator for the routes of this table.
     *
     * @return An iterator.
     */
    public RouteIterator getRouteIterator() {
        return new RouteIterator(routes);
    }

    /**
     * Returns whether or not there are any routes in this routing table.
     *
     * @return True if there is at least one route.
     */
    public boolean hasRoutes() {
        return !routes.isEmpty();
    }

    /**
     * Returns the number of routes that are contained in this.
     *
     * @return The number of routes.
     */
    public int getNumRoutes() {
        return routes.size();
    }

    /**
     * Returns whether or not there exists a named hop in this.
     *
     * @param name The name of the hop to look for.
     * @return True if the named hop exists.
     */
    public boolean hasHop(String name) {
        return hops.containsKey(name);
    }

    /**
     * Returns the named hop, may be null.
     *
     * @param name The name of the hop to return.
     * @return The hop implementation object.
     */
    public HopBlueprint getHop(String name) {
        return hops.get(name);
    }

    /**
     * Returns whether or not there exists a named route in this.
     *
     * @param name The name of the route to look for.
     * @return True if the named route exists.
     */
    public boolean hasRoute(String name) {
        return routes.containsKey(name);
    }

    /**
     * Returns the named route, may be null.
     *
     * @param name The name of the route to return.
     * @return The route implementation object.
     */
    public Route getRoute(String name) {
        return routes.get(name);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("RoutingTable(hops = { ");
        int i = 0;
        for (String name : hops.keySet()) {
            ret.append("'").append(name).append("' : ").append(hops.get(name));
            if (i++ < hops.size() - 1) {
                ret.append(", ");
            }
        }
        ret.append(" }, routes = { ");
        i = 0;
        for (String name : routes.keySet()) {
            ret.append("'").append(name).append("' : ").append(routes.get(name));
            if (i++ < routes.size()) {
                ret.append(", ");
            }
        }
        ret.append(" })");
        return ret.toString();
    }

    /**
     * Implements an iterator for the hops of this. Use {@link RoutingTable#getHopIterator()}
     * to retrieve an instance of this.
     */
    public static class HopIterator {

        private Iterator<Map.Entry<String, HopBlueprint>> it;
        private Map.Entry<String, HopBlueprint> entry;

        /**
         * Constructs a new iterator based on a given map. This is private so that only a {@link RoutingTable} can
         * create one.
         *
         * @param hops The map to iterate through.
         */
        private HopIterator(Map<String, HopBlueprint> hops) {
            it = hops.entrySet().iterator();
            next();
        }

        /**
         * Steps to the next hop in the map.
         */
        public void next() {
            entry = it.hasNext() ? it.next() : null;
        }

        /**
         * Returns whether or not this iterator is valid.
         *
         * @return True if valid.
         */
        public boolean isValid() {
            return entry != null;
        }

        /**
         * Returns the name of the current hop.
         *
         * @return The name.
         */
        public String getName() {
            return entry.getKey();
        }

        /**
         * Returns the current hop.
         *
         * @return The hop.
         */
        public HopBlueprint getHop() {
            return entry.getValue();
        }
    }

    /**
     * Implements an iterator for the routes of this. Use {@link RoutingTable#getRouteIterator()}
     * to retrieve an instance of this.
     */
    public static class RouteIterator {

        private Iterator<Map.Entry<String, Route>> it;
        private Map.Entry<String, Route> entry;

        /**
         * Constructs a new iterator based on a given map. This is private so that only a {@link RoutingTable} can
         * create one.
         *
         * @param routes The map to iterate through.
         */
        private RouteIterator(Map<String, Route> routes) {
            it = routes.entrySet().iterator();
            next();
        }

        /**
         * Steps to the next route in the map.
         */
        public void next() {
            entry = it.hasNext() ? it.next() : null;
        }

        /**
         * Returns whether or not this iterator is valid.
         *
         * @return True if valid.
         */
        public boolean isValid() {
            return entry != null;
        }

        /**
         * Returns the name of the current route.
         *
         * @return The name.
         */
        public String getName() {
            return entry.getKey();
        }

        /**
         * Returns the current route.
         *
         * @return The route.
         */
        public Route getRoute() {
            return entry.getValue();
        }
    }
}
