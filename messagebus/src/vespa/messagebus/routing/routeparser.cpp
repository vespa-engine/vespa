// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "errordirective.h"
#include "policydirective.h"
#include "routedirective.h"
#include "routeparser.h"
#include "tcpdirective.h"
#include "verbatimdirective.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::stringref;

namespace mbus {

bool
RouteParser::isWhitespace(char c)
{
    return c == ' ' || c == '\f' || c == '\n' || c == '\r' || c == '\t';
}

IHopDirective::SP
RouteParser::createRouteDirective(const stringref &str)
{
    return IHopDirective::SP(new RouteDirective(str));
}

IHopDirective::SP
RouteParser::createTcpDirective(const stringref &str)
{
    size_t posP = str.find(":");
    if (posP == string::npos || posP == 0) {
        return IHopDirective::SP(); // no host
    }
    size_t posS = str.find("/", posP);
    if (posS == string::npos || posS == posP + 1) {
        return IHopDirective::SP(); // no port
    }
    return IHopDirective::SP(new TcpDirective(str.substr(0, posP),
                                              atoi(str.substr(posP + 1, posS - 1).c_str()),
                                              str.substr(posS + 1)));
}

IHopDirective::SP
RouteParser::createPolicyDirective(const stringref &str)
{
    size_t pos = str.find(":");
    if (pos == string::npos) {
        return IHopDirective::SP(new PolicyDirective(str, ""));
    }
    return IHopDirective::SP(new PolicyDirective(str.substr(0, pos), str.substr(pos + 1)));
}

IHopDirective::SP
RouteParser::createVerbatimDirective(const stringref &str)
{
    return IHopDirective::SP(new VerbatimDirective(str));
}

IHopDirective::SP
RouteParser::createErrorDirective(const stringref &str)
{
    return IHopDirective::SP(new ErrorDirective(str));
}

IHopDirective::SP
RouteParser::createDirective(const stringref &str)
{
    if (str.size() > 2 && str[0] == '[') {
        return createPolicyDirective(str.substr(1, str.size() - 2));
    }
    return createVerbatimDirective(str);
}

Hop
RouteParser::createHop(stringref str)
{
    if (str.empty()) {
        return Hop().addDirective(createErrorDirective("Failed to parse empty string."));
    }
    size_t len = str.size();
    if (len > 1 && str[0] == '?') {
        Hop hop = createHop(str.substr(1));
        hop.setIgnoreResult(true);
        return hop;
    }
    if (len > 4 && str.substr(0, 4) == "tcp/") {
        IHopDirective::SP tcp = createTcpDirective(str.substr(4));
        if (tcp.get() != nullptr) {
            return Hop().addDirective(tcp);
        }
    }
    if (len > 6 && str.substr(0, 6) == "route:") {
        return Hop().addDirective(createRouteDirective(str.substr(6)));
    }
    Hop ret;
    for (size_t from = 0, at = 0, depth = 0; at <= len; ++at) {
        if (at == len || (depth == 0 && str[at] == '/')) {
            if (depth > 0) {
                return Hop().addDirective(createErrorDirective(
                                "Unexpected token '': syntax error"));
            }
            ret.addDirective(createDirective(str.substr(from, at - from)));
            from = at + 1;
        } else if (isWhitespace(str[at]) && depth == 0) {
            return Hop().addDirective(createErrorDirective(
                            vespalib::make_string(
                                    "Failed to completely parse '%s'.",
                                    str.c_str())));
        } else if (str[at] == '[') {
            ++depth;
        } else if (str[at] == ']') {
            if (depth == 0) {
                return Hop().addDirective(createErrorDirective(
                                "Unexpected token ']': syntax error"));
            }
            --depth;
        }
    }
    return ret;
}

Route
RouteParser::createRoute(stringref str)
{
    Route ret;
    for (size_t from = 0, at = 0, depth = 0; at <= str.size(); ++at) {
        if (at == str.size() || (depth == 0 && isWhitespace(str[at]))) {
            if (from < at - 1) {
                Hop hop = createHop(str.substr(from, at - from));
                if (hop.hasDirectives() &&
                    hop.getDirective(0)->getType() == IHopDirective::TYPE_ERROR)
                {
                    return std::move(Route().addHop(std::move(hop)));
                }
                ret.addHop(std::move(hop));
            }
            from = at + 1;
        } else if (str[at] == '[') {
            ++depth;
        } else if (str[at] == ']') {
            --depth;
        }
    }
    return ret;
}

} // mbus
