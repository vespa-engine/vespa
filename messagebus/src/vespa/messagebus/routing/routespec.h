// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace mbus {

/**
 * Along with the {@link RoutingSpec}, {@link RoutingTableSpec} and {@link HopSpec}, this holds the routing
 * specifications for all protocols. The only way a client can configure or alter the settings of a message bus instance
 * is through these classes.
 *
 * This class contains the spec for a single route.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class RouteSpec {
private:
    string              _name;
    std::vector<string> _hops;

public:
    /**
     * The default constructor assigns a value to the immutable name variable.
     *
     * @param name A protocol-unique name for this route.
     */
    RouteSpec(const string &name);
    RouteSpec(const RouteSpec &);
    RouteSpec & operator = (const RouteSpec &);
    RouteSpec(RouteSpec &&) = default;
    RouteSpec & operator = (RouteSpec &&) = default;

    ~RouteSpec();

    /**
     * Returns the protocol-unique name of this route.
     *
     * @return The name.
     */
    const string &getName() const { return _name; }

    /**
     * Returns the hop name at the given index.
     *
     * @param i The index of the hop to return.
     * @return The hop at the given index.
     */
    const string &getHop(uint32_t i) const { return _hops[i]; }

    /**
     * Returns whether or not there are any hops in this route.
     *
     * @return True if there is at least one hop.
     */
    bool hasHops() const { return !_hops.empty(); }

    /**
     * Returns the number of hops that make up this route.
     *
     * @return The number of hops.
     */
    uint32_t getNumHops() const { return _hops.size(); }

    /**
     * Adds the given hop name to this.
     *
     * @param hop The hop to add.
     * @return This, to allow chaining.
     */
    RouteSpec &addHop(const string &hop) { _hops.push_back(hop); return *this; }

    /**
     * Adds the given hop names to this.
     *
     * @param hops The hops to add.
     * @return This, to allow chaining.
     */
    RouteSpec &addHops(const std::vector<string> &hops);

    /**
     * Sets the hop name for a given index.
     *
     * @param i   The index of the hop to set.
     * @param hop The hop to set.
     * @return This, to allow chaining.
     */
    RouteSpec &setHop(uint32_t i, const string &hop) { _hops[i] = hop; return *this; }

    /**
     * Removes the hop name at the given index.
     *
     * @param i The index of the hop to remove.
     * @return The removed hop.
     */
    string removeHop(uint32_t i);

    /**
     * Clears the list of hops that make up this route.
     *
     * @return This, to allow chaining.
     */
    RouteSpec &clearHops() { _hops.clear(); return *this; }

    /**
     * Appends the content of this to the given config string.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    void toConfig(string &cfg, const string &prefix) const;

    /**
     * Returns a string representation of this route specification.
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
    bool operator==(const RouteSpec &rhs) const;

    /**
     * Implements the inequality operator.
     *
     * @param rhs The object to compare to.
     * @return True if this does not equals the other.
     */
    bool operator!=(const RouteSpec &rhs) const { return !(*this == rhs); }
};

} // namespace mbus

