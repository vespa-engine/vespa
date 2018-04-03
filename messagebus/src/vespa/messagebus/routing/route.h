// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hop.h"

namespace mbus {

class Hop;

/**
 * A route is a list of {@link Hop hops} that are resolved from first to last as a routable moves from source to
 * destination. A route may be changed at any time be either application logic or an invoked {@link RoutingPolicy}, so
 * no guarantees on actual path can be given without the full knowledge of all that logic.
 *
 * To construct a route you may either use the factory method {@link this#parse(String)} to produce a route instance
 * from a string representation, or you may build one programatically through the hop accessors.
 */
class Route {
private:
    std::vector<Hop> _hops;

public:
    /**
     * Convenience typedef for an auto-pointer to a route.
     */
    typedef std::unique_ptr<Route> UP;

    /**
     * Parses the given string as a list of space-separated hops. The {@link this#toString()} method is compatible with
     * this parser.
     *
     * @param route The string to parse.
     * @return A route that corresponds to the string.
     */
    static Route parse(vespalib::stringref route);

    /**
     * Create a Route that contains no hops
     */
    Route();
    Route(const Route &) = default;
    Route & operator = (const Route &) = default;
    Route(Route &&) noexcept = default;
    Route & operator = (Route && ) noexcept = default;
    ~Route();

    /**
     * Constructs a route that contains the given hops.
     *
     * @param hops The hops to instantiate with.
     */
    Route(std::vector<Hop> hops);

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
     * Returns the hop at the given index.
     *
     * @param i The index of the hop to return.
     * @return The hop.
     */
    Hop &getHop(uint32_t i) { return _hops[i]; }

    /**
     * Returns a const reference to the hop at the given index.
     *
     * @param i The index of the hop to return.
     * @return The hop.
     */
    const Hop &getHop(uint32_t i) const { return _hops[i]; }

    /**
     * Adds a hop to the list of hops that make up this route.
     *
     * @param hop The hop to add.
     * @return This, to allow chaining.
     */
    Route &addHop(Hop hop);

    /**
     * Sets the hop at a given index.
     *
     * @param i   The index at which to set the hop.
     * @param hop The hop to set.
     * @return This, to allow chaining.
     */
    Route &setHop(uint32_t i, Hop hop);

    /**
     * Removes the hop at a given index.
     *
     * @param i The index of the hop to remove.
     * @return The hop removed.
     */
    Hop removeHop(uint32_t i);

    /**
     * Clears the list of hops that make up this route.
     *
     * @return This, to allow chaining.
     */
    Route &clearHops();

    /**
     * Returns a string representation of this route.
     *
     * @return A string representation.
     */
    string toString() const;

    /**
     * Returns a string representation of this that can be debugged but not parsed.
     *
     * @return The debug string.
     */
    string toDebugString() const;
};

} // namespace mbus

