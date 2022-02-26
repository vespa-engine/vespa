// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include "hopspec.h"
#include "routespec.h"

namespace mbus {

/**
 * Along with the {@link RoutingSpec}, {@link RouteSpec} and {@link HopSpec}, this holds the routing specifications for
 * all protocols. The only way a client can configure or alter the settings of a message bus instance is through these
 * classes.
 *
 * This class contains the spec for a single routing table, which corresponds to exactly one protocol.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class RoutingTableSpec {
private:
    string            _protocol;
    std::vector<HopSpec>   _hops;
    std::vector<RouteSpec> _routes;

public:
    /**
     * Creates a new routing table specification for a named protocol.
     *
     * @param protocol The name of the protocol that this belongs to.
     */
    RoutingTableSpec(const string &protocol);
    RoutingTableSpec(const RoutingTableSpec&);
    RoutingTableSpec(RoutingTableSpec&&) noexcept = default;
    ~RoutingTableSpec();
    RoutingTableSpec &operator=(const RoutingTableSpec&);
    RoutingTableSpec &operator=(RoutingTableSpec&&) noexcept = default;

    /**
     * Returns the name of the protocol that this is the routing table for.
     *
     * @return The protocol name.
     */
    const string &getProtocol() const { return _protocol; }

    /**
     * Returns whether or not there are any hop specs contained in this.
     *
     * @return True if there is at least one hop.
     */
    bool hasHops() const { return !_hops.empty(); }

    /**
     * Returns the number of hops that are contained in this table.
     *
     * @return The number of hops.
     */
    uint32_t getNumHops() const { return _hops.size(); }

    /**
     * Returns the hop spec at the given index.
     *
     * @param i The index of the hop to return.
     * @return The hop at the given position.
     */
    HopSpec &getHop(uint32_t i) { return _hops[i]; }

    /**
     * Returns a const reference to the hop spec at the given index.
     *
     * @param i The index of the hop to return.
     * @return The hop at the given position.
     */
    const HopSpec &getHop(uint32_t i) const { return _hops[i]; }

    /**
     * Adds the given hop spec to this.
     *
     * @param hop The hop to add.
     * @return This, to allow chaining.
     */
    RoutingTableSpec &addHop(const HopSpec &hop) { _hops.push_back(hop); return *this; }

    /**
     * Sets the hop spec at the given index.
     *
     * @param i   The index at which to set the hop.
     * @param hop The hop to set.
     * @return This, to allow chaining.
     */
    RoutingTableSpec &setHop(uint32_t i, const HopSpec &hop) { _hops[i] = hop; return *this; }

    /**
     * Removes the hop spec at the given index.
     *
     * @param i The index of the hop to remove.
     * @return The removed hop.
     */
    HopSpec removeHop(uint32_t i);

    /**
     * Clears the list of hop specs contained in this.
     *
     * @return This, to allow chaining.
     */
    RoutingTableSpec &clearHops() { _hops.clear(); return *this; }

    /**
     * Returns whether or not there are any route specs contained in this.
     *
     * @return True if there is at least one route.
     */
    bool hasRoutes() const { return !_routes.empty(); }

    /**
     * Returns the number of route specs contained in this.
     *
     * @return The number of routes.
     */
    uint32_t getNumRoutes() const { return _routes.size(); }

    /**
     * Returns the route spec at the given index.
     *
     * @param i The index of the route to return.
     * @return The route at the given index.
     */
    RouteSpec &getRoute(uint32_t i) { return _routes[i]; }

    /**
     * Returns a const reference to the route spec at the given index.
     *
     * @param i The index of the route to return.
     * @return The route at the given index.
     */
    const RouteSpec &getRoute(uint32_t i) const { return _routes[i]; }

    /**
     * Adds a route spec to this.
     *
     * @param route The route to add.
     * @return This, to allow chaining.
     */
    RoutingTableSpec &addRoute(const RouteSpec &route) { _routes.push_back(route); return *this; }

    /**
     * Sets the route spec at the given index.
     *
     * @param i     The index at which to set the route.
     * @param route The route to set.
     * @return This, to allow chaining.
     */
    RoutingTableSpec &setRoute(uint32_t i, const RouteSpec &route) { _routes[i] = route; return *this; }

    /**
     * Removes a route spec at a given index.
     *
     * @param i The index of the route to remove.
     * @return The removed route.
     */
    RouteSpec removeRoute(uint32_t i);

    /**
     * Appends the content of this to the given config string.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    void toConfig(string &cfg, const string &prefix) const;

    /**
     * Returns a string representation of this.
     *
     * @return The string.
     */
    string toString() const;

    /**
     * Implements the equality operator.
     *
     * @param rhs The object to compare to.
     * @return True if this equals the other.
     */
    bool operator==(const RoutingTableSpec &rhs) const;

    /**
     * Implements the inequality operator.
     *
     * @param rhs The object to compare to.
     * @return True if this does not equals the other.
     */
    bool operator!=(const RoutingTableSpec &rhs) const { return !(*this == rhs); }
};

} // namespace mbus

