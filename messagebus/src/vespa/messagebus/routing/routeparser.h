// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hop.h"
#include "route.h"

namespace mbus {

/**
 * This is a convenient entry point into creating a route or hop object. You can
 * either build a routing object by hand, but using the factory method {@link
 * #create} with a string is far simpler.
 */
class RouteParser {
private:
    static bool isWhitespace(char c);
    static IHopDirective::SP createDirective(vespalib::stringref str);
    static IHopDirective::SP createErrorDirective(vespalib::stringref str);
    static IHopDirective::SP createPolicyDirective(vespalib::stringref str);
    static IHopDirective::SP createRouteDirective(vespalib::stringref str);
    static IHopDirective::SP createTcpDirective(vespalib::stringref str);
    static IHopDirective::SP createVerbatimDirective(vespalib::stringref str);

public:
    /**
     * Creates a hop from a string representation.
     *
     * @param str The string to parse as a hop.
     * @return The created hop.
     */
    static Hop createHop(vespalib::stringref str);

    /**
     * Creates a route from a string representation.
     *
     * @param str The string to parse as a route.
     * @return The created route.
     */
    static Route createRoute(vespalib::stringref str);
};

} // mbus

