// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.text.Utf8String;

import java.util.*;

/**
 * Along with the {@link RoutingSpec}, {@link RouteSpec} and {@link HopSpec}, this holds the routing specifications for
 * all protocols. The only way a client can configure or alter the settings of a message bus instance is through these
 * classes.
 * <p>
 * This class contains the spec for a single routing table, which corresponds to exactly one protocol.
 *
 * @author Simon Thoresen Hult
 */
public class RoutingTableSpec {

    private final String protocol;
    private final List<HopSpec> hops = new ArrayList<>();
    private final List<RouteSpec> routes = new ArrayList<>();
    private final boolean verify;

    /**
     * Creates a new routing table specification for a named protocol.
     *
     * @param protocol The name of the protocol that this belongs to.
     */
    public RoutingTableSpec(String protocol) {
        this(protocol, true);
    }
    /**
     * Creates a new routing table specification for a named protocol.
     *
     * @param protocol The name of the protocol that this belongs to.
     */
    public RoutingTableSpec(Utf8String protocol) {
        this(protocol.toString(), true);
    }

    /**
     * Creates a new routing table specification for a named protocol.
     *
     * @param protocol The name of the protocol that this belongs to.
     * @param verify   Whether or not this should be verified.
     */
    public RoutingTableSpec(String protocol, boolean verify) {
        this.protocol = protocol;
        this.verify = verify;
    }

    /**
     * Implements the copy constructor.
     *
     * @param obj The object to copy.
     */
    public RoutingTableSpec(RoutingTableSpec obj) {
        this.protocol = obj.protocol;
        this.verify = obj.verify;
        for (HopSpec hop : obj.hops) {
            hops.add(new HopSpec(hop));
        }
        for (RouteSpec route : obj.routes) {
            routes.add(new RouteSpec(route));
        }
    }

    /**
     * Returns the name of the protocol that this is the routing table for.
     *
     * @return The protocol name.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Returns whether or not there are any hop specs contained in this.
     *
     * @return True if there is at least one hop.
     */
    public boolean hasHops() {
        return !hops.isEmpty();
    }

    /**
     * Returns whether or not there is a named hop spec contained in this.
     *
     * @param hopName The hop name to check for.
     * @return True if the hop exists.
     */
    public boolean hasHop(String hopName) {
        for (HopSpec hop : hops) {
            if (hop.getName().equals(hopName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of hops that are contained in this table.
     *
     * @return The number of hops.
     */
    public int getNumHops() {
        return hops.size();
    }

    /**
     * Returns the hop spec at the given index.
     *
     * @param i The index of the hop to return.
     * @return The hop at the given position.
     */
    public HopSpec getHop(int i) {
        return hops.get(i);
    }

    /**
     * Adds the given hop spec to this.
     *
     * @param hop The hop to add.
     * @return This, to allow chaining.
     */
    public RoutingTableSpec addHop(HopSpec hop) {
        hops.add(hop);
        return this;
    }

    /**
     * Sets the hop spec at the given index.
     *
     * @param i   The index at which to set the hop.
     * @param hop The hop to set.
     * @return This, to allow chaining.
     */
    public RoutingTableSpec setHop(int i, HopSpec hop) {
        hops.set(i, hop);
        return this;
    }

    /**
     * Removes the hop spec at the given index.
     *
     * @param i The index of the hop to remove.
     * @return The removed hop.
     */
    public HopSpec removeHop(int i) {
        return hops.remove(i);
    }

    /**
     * Clears the list of hop specs contained in this.
     *
     * @return This, to allow chaining.
     */
    public RoutingTableSpec clearHops() {
        hops.clear();
        return this;
    }

    /**
     * Returns whether or not there are any route specs contained in this.
     *
     * @return True if there is at least one route.
     */
    public boolean hasRoutes() {
        return !routes.isEmpty();
    }

    /**
     * Returns whether or not there is a named route spec contained in this.
     *
     * @param routeName The hop name to check for.
     * @return True if the hop exists.
     */
    public boolean hasRoute(String routeName) {
        for (RouteSpec route : routes) {
            if (route.getName().equals(routeName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of route specs contained in this.
     *
     * @return The number of routes.
     */
    public int getNumRoutes() {
        return routes.size();
    }

    /**
     * Returns the route spec at the given index.
     *
     * @param i The index of the route to return.
     * @return The route at the given index.
     */
    public RouteSpec getRoute(int i) {
        return routes.get(i);
    }

    /**
     * Adds a route spec to this.
     *
     * @param route The route to add.
     * @return This, to allow chaining.
     */
    public RoutingTableSpec addRoute(RouteSpec route) {
        routes.add(route);
        return this;
    }

    /**
     * Sets the route spec at the given index.
     *
     * @param i     The index at which to set the route.
     * @param route The route to set.
     * @return This, to allow chaining.
     */
    public RoutingTableSpec setRoute(int i, RouteSpec route) {
        routes.set(i, route);
        return this;
    }

    /**
     * Removes a route spec at a given index.
     *
     * @param i The index of the route to remove.
     * @return The removed route.
     */
    public RouteSpec removeRoute(int i) {
        return routes.remove(i);
    }

    /**
     * Clears the list of routes that are contained in this.
     *
     * @return This, to allow chaining.
     */
    public RoutingTableSpec clearRoutes() {
        routes.clear();
        return this;
    }

    /**
     * A convenience function to add a new hop to this routing table.
     *
     * @param name       A protocol-unique name for this hop.
     * @param selector   A string that represents the selector for this hop.
     * @param recipients A list of recipients for this hop.
     * @return This, to allow chaining.
     */
    public RoutingTableSpec addHop(String name, String selector, List<String> recipients) {
        return addHop(new HopSpec(name, selector).addRecipients(recipients));
    }

    /**
     * A convenience function to add a new route to this routing table.
     *
     * @param name A protocol-unique name for this route.
     * @param hops A list of hops for this route.
     * @return This, to allow chaining.
     */
    public RoutingTableSpec addRoute(String name, List<String> hops) {
        return addRoute(new RouteSpec(name).addHops(hops));
    }

    /**
     * Verifies the content of this against the given application.
     *
     * @param app    The application to verify against.
     * @param errors The list of errors found.
     * @return True if no errors where found.
     */
    public boolean verify(ApplicationSpec app, List<String> errors) {
        if (verify) {
            // Verify and count hops.
            Map<String, Integer> hopNames = new HashMap<String, Integer>();
            for (HopSpec hop : hops) {
                String name = hop.getName();
                int count = hopNames.containsKey(name) ? hopNames.get(name) : 0;
                hopNames.put(name, count + 1);
                hop.verify(app, this, errors);
            }
            for (Map.Entry<String, Integer> entry : hopNames.entrySet()) {
                int count = entry.getValue();
                if (count > 1) {
                    errors.add("Hop '" + entry.getKey() + "' in routing table '" + protocol + "' is defined " +
                               count + " times.");
                }
            }

            // Verify and count routes.
            Map<String, Integer> routeNames = new HashMap<String, Integer>();
            for (RouteSpec route : routes) {
                String name = route.getName();
                int count = routeNames.containsKey(name) ? routeNames.get(name) : 0;
                routeNames.put(name, count + 1);
                route.verify(app, this, errors);
            }
            for (Map.Entry<String, Integer> entry : routeNames.entrySet()) {
                int count = entry.getValue();
                if (count > 1) {
                    errors.add("Route '" + entry.getKey() + "' in routing table '" + protocol + "' is defined " +
                               count + " times.");
                }
            }
        }
        return errors.isEmpty();
    }

    /**
     * Sorts the hops and routes of this table by name. This is useful for generating a stable config for testing.
     */
    public void sort() {
        Collections.sort(hops, new Comparator<HopSpec>() {
            public int compare(HopSpec lhs, HopSpec rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });
        Collections.sort(routes, new Comparator<RouteSpec>() {
            public int compare(RouteSpec lhs, RouteSpec rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });
    }

    /**
     * Appends the content of this to the given config string builder.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    public void toConfig(StringBuilder cfg, String prefix) {
        cfg.append(prefix).append("protocol ").append(RoutingSpec.toConfigString(protocol)).append("\n");
        int numHops = hops.size();
        if (numHops > 0) {
            cfg.append(prefix).append("hop[").append(numHops).append("]\n");
            for (int i = 0; i < numHops; ++i) {
                hops.get(i).toConfig(cfg, prefix + "hop[" + i + "].");
            }
        }
        int numRoutes = routes.size();
        if (numRoutes > 0) {
            cfg.append(prefix).append("route[").append(numRoutes).append("]\n");
            for (int i = 0; i < numRoutes; ++i) {
                routes.get(i).toConfig(cfg, prefix + "route[" + i + "].");
            }
        }
    }

    // Overrides Object.
    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        toConfig(ret, "");
        return ret.toString();
    }

    // Overrides Object.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RoutingTableSpec)) {
            return false;
        }
        RoutingTableSpec rhs = (RoutingTableSpec)obj;
        if (!protocol.equals(rhs.protocol)) {
            return false;
        }
        if (!hops.equals(rhs.hops)) {
            return false;
        }
        if (!routes.equals(rhs.routes)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = protocol != null ? protocol.hashCode() : 0;
        result = 31 * result + (hops != null ? hops.hashCode() : 0);
        result = 31 * result + (routes != null ? routes.hashCode() : 0);
        result = 31 * result + (verify ? 1 : 0);
        return result;
    }
}
