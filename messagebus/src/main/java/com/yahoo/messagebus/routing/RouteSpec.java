// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Along with the {@link RoutingSpec}, {@link RoutingTableSpec} and {@link HopSpec}, this holds the routing
 * specifications for all protocols. The only way a client can configure or alter the settings of a message bus instance
 * is through these classes.
 * <p>
 * This class contains the spec for a single route.
 *
 * @author Simon Thoresen Hult
 */
public class RouteSpec {

    private final String name;
    private final List<String> hops = new ArrayList<String>();
    private final boolean verify;

    /**
     * Creates a new named route specification.
     *
     * @param name A protocol-unique name for this route.
     */
    public RouteSpec(String name) {
        this(name, true);
    }

    /**
     * Creates a new named route specification.
     *
     * @param name   A protocol-unique name for this route.
     * @param verify Whether or not this should be verified.
     */
    public RouteSpec(String name, boolean verify) {
        this.name = name;
        this.verify = verify;
    }

    /**
     * Implements the copy constructor.
     *
     * @param obj The object to copy.
     */
    public RouteSpec(RouteSpec obj) {
        this.name = obj.name;
        this.verify = obj.verify;
        for (String hop : obj.hops) {
            hops.add(hop);
        }
    }

    /**
     * Returns the protocol-unique name of this route.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the hop name at the given index.
     *
     * @param i The index of the hop to return.
     * @return The hop at the given index.
     */
    public String getHop(int i) {
        return hops.get(i);
    }

    /**
     * Returns whether or not there are any hops in this route.
     *
     * @return True if there is at least one hop.
     */
    public boolean hasHops() {
        return !hops.isEmpty();
    }

    /**
     * Returns the number of hops that make up this route.
     *
     * @return The number of hops.
     */
    public int getNumHops() {
        return hops.size();
    }

    /**
     * Adds the given hop name to this.
     *
     * @param hop The hop to add.
     * @return This, to allow chaining.
     */
    public RouteSpec addHop(String hop) {
        hops.add(hop);
        return this;
    }

    /**
     * Adds the given hop names to this.
     *
     * @param hops The hops to add.
     * @return This, to allow chaining.
     */
    public RouteSpec addHops(List<String> hops) {
        this.hops.addAll(hops);
        return this;
    }

    /**
     * Sets the hop name for a given index.
     *
     * @param i   The index of the hop to set.
     * @param hop The hop to set.
     * @return This, to allow chaining.
     */
    public RouteSpec setHop(int i, String hop) {
        hops.set(i, hop);
        return this;
    }

    /**
     * Removes the hop name at the given index.
     *
     * @param i The index of the hop to remove.
     * @return The removed hop.
     */
    public String removeHop(int i) {
        return hops.remove(i);
    }

    /**
     * Clears the list of hops that make up this route.
     *
     * @return This, to allow chaining.
     */
    public RouteSpec clearHops() {
        hops.clear();
        return this;
    }

    /**
     * Verifies the content of this against the given application.
     *
     * @param app    The application to verify against.
     * @param table  The routing table to verify against.
     * @param errors The list of errors found.
     * @return True if no errors where found.
     */
    public boolean verify(ApplicationSpec app, RoutingTableSpec table, List<String> errors) {
        if (verify) {
            String protocol = table.getProtocol();
            int numHops = hops.size();
            if (numHops == 0) {
                errors.add("Route '" + name + "' in routing table '" + protocol + "' has no hops.");
            } else {
                for (int i = 0; i < numHops; ++i) {
                    HopSpec.verify(app, table, null, null, hops.get(i), errors,
                                   "hop " + (i + 1) + " in route '" + name + "' in routing table '" + protocol + "'");
                }
            }
        }
        return errors.isEmpty();
    }

    /**
     * Appends the content of this to the given config string builder.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    public void toConfig(StringBuilder cfg, String prefix) {
        cfg.append(prefix).append("name ").append(RoutingSpec.toConfigString(name)).append("\n");
        int numHops = hops.size();
        if (numHops > 0) {
            cfg.append(prefix).append("hop[").append(numHops).append("]\n");
            for (int i = 0; i < numHops; ++i) {
                cfg.append(prefix).append("hop[").append(i).append("] ");
                cfg.append(RoutingSpec.toConfigString(hops.get(i))).append("\n");
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
        if (!(obj instanceof RouteSpec)) {
            return false;
        }
        RouteSpec rhs = (RouteSpec)obj;
        if (!name.equals(rhs.name)) {
            return false;
        }
        if (!hops.equals(rhs.hops)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (hops != null ? hops.hashCode() : 0);
        result = 31 * result + (verify ? 1 : 0);
        return result;
    }
}
