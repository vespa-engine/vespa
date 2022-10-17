// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ihopdirective.h"

namespace mbus {

/**
 * This class represents a route directive within a {@link Hop}'s selector. This will be replaced by the named route
 * when evaluated. If the route is not present in the running protocol's routing table, routing will fail.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class RouteDirective : public IHopDirective {
private:
    string _name;

public:

    /**
     * Constructs a new directive to insert a route.
     *
     * @param name The name of the route to insert.
     */
    RouteDirective(vespalib::stringref name);
    ~RouteDirective() override;

    /**
     * Returns the name of the route to insert.
     *
     * @return The name name.
     */
    const string &getName() const { return _name; }

    Type getType() const override { return TYPE_ROUTE; }
    bool matches(const IHopDirective &dir) const override;
    string toString() const override;
    string toDebugString() const override;
};

} // mbus

