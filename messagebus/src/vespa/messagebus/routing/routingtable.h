// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <string>
#include "hopblueprint.h"
#include "route.h"

namespace mbus {

class RoutingTableSpec;
class IProtocol;
class INetwork;
class IReplyHandler;
class Message;

/**
 * At any time there may only ever be zero or one routing table registered in message bus for each protocol. This class
 * contains a list of named hops and routes that may be used to substitute references to these during route resolving.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class RoutingTable {
private:
    string                         _name;
    std::map<string, HopBlueprint> _hops;
    std::map<string, Route>        _routes;

public:
    /**
     * Implements an iterator for the hops contained in this table.
     */
    class HopIterator {
    private:
        std::map<string, HopBlueprint>::const_iterator _pos, _end;

    public:
        HopIterator(const std::map<string, HopBlueprint> &hops);

        bool isValid()               { return _pos != _end; }
        void next()                  { ++_pos; }
        const string &getName() { return _pos->first; }
        const HopBlueprint &getHop() { return _pos->second; }
    };

    /**
     * Implements an iterator for the routes contained in this table.
     */
    class RouteIterator {
    private:
        std::map<string, Route>::const_iterator _pos, _end;

    public:
        RouteIterator(const std::map<string, Route> &hops);

        bool isValid()               { return _pos != _end; }
        void next()                  { ++_pos; }
        const string &getName() { return _pos->first; }
        const Route &getRoute()      { return _pos->second; }
    };

    /**
     * Convenience typedef for a shared pointer to a RoutingTable object.
     */
    typedef std::shared_ptr<RoutingTable> SP;
    RoutingTable(const RoutingTable &) = delete;
    RoutingTable & operator = (const RoutingTable &) = delete;

    /**
     * Creates a new routing table based on a given specification. This also verifies the integrity of the table.
     *
     * @param spec The specification to use.
     */
    RoutingTable(const RoutingTableSpec &spec);

    /**
     * Returns whether or not there are any hops in this routing table.
     *
     * @return True if there is at least one hop.
     */
    bool hasHops() const { return !_hops.empty(); }

    /**
     * Returns the number of hops that are contained in this.
     *
     * @return The number of hops.
     */
    uint32_t getNumHops() const { return _hops.size(); }

    /**
     * Returns whether or not there exists a named hop in this.
     *
     * @param name The name of the hop to look for.
     * @return True if the named hop exists.
     */
    bool hasHop(const string &name) const;

    /**
     * Returns the named hop, may be null.
     *
     * @param name The name of the hop to return.
     * @return The hop implementation object.
     */
    const HopBlueprint *getHop(const string &name) const;

    /**
     * Returns an iterator for the hops of this.
     *
     * @return An iterator.
     */
    HopIterator getHopIterator() const { return HopIterator(_hops); }

    /**
     * Returns whether or not there are any routes in this routing table.
     *
     * @return True if there is at least one route.
     */
    bool hasRoutes() const { return !_routes.empty(); }

    /**
     * Returns the number of routes that are contained in this.
     *
     * @return The number of routes.
     */
    uint32_t getNumRoutes() const { return _routes.size(); }

    /**
     * Returns whether or not there exists a named route in this.
     *
     * @param name The name of the route to look for.
     * @return True if the named route exists.
     */
    bool hasRoute(const string &name) const;

    /**
     * Returns the named route, may be null.
     *
     * @param name The name of the route to return.
     * @return The route implementation object.
     */
    const Route *getRoute(const string &name) const;

    /**
     * Returns an iterator for the routes of this.
     *
     * @return An iterator.
     */
    RouteIterator getRouteIterator() const { return RouteIterator(_routes); }
};

} // namespace mbus

