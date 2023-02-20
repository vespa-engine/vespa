// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routingspec.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

RoutingTableSpec::RoutingTableSpec(const string &protocol) :
    _protocol(protocol),
    _hops(),
    _routes()
{ }

RoutingTableSpec::RoutingTableSpec(const RoutingTableSpec&) = default;

RoutingTableSpec::~RoutingTableSpec() = default;

RoutingTableSpec& RoutingTableSpec::operator=(const RoutingTableSpec&) = default;

RoutingTableSpec &
RoutingTableSpec::addHop(HopSpec && hop) & {
    _hops.emplace_back(std::move(hop));
    return *this;
}
RoutingTableSpec &&
RoutingTableSpec::addHop(HopSpec && hop) && {
    _hops.emplace_back(std::move(hop));
    return std::move(*this);
}

RoutingTableSpec &
RoutingTableSpec::setHop(uint32_t i, HopSpec &&hop) {
    _hops[i] = std::move(hop);
    return *this;
}

RoutingTableSpec &
RoutingTableSpec::addRoute(RouteSpec &&route) & {
    _routes.emplace_back(std::move(route));
    return *this;
}

RoutingTableSpec &&
RoutingTableSpec::addRoute(RouteSpec &&route) && {
    _routes.emplace_back(std::move(route));
    return std::move(*this);
}

void
RoutingTableSpec::toConfig(string &cfg, const string &prefix) const
{
    cfg.append(prefix).append("protocol ").append(RoutingSpec::toConfigString(_protocol)).append("\n");
    uint32_t numHops = _hops.size();
    if (numHops > 0) {
        cfg.append(prefix).append("hop[").append(make_string("%d", numHops)).append("]\n");
        for (uint32_t i = 0; i < numHops; ++i) {
            _hops[i].toConfig(cfg, make_string("%shop[%d].", prefix.c_str(), i));
        }
    }
    uint32_t numRoutes = _routes.size();
    if (numRoutes > 0) {
        cfg.append(prefix).append("route[").append(make_string("%d", numRoutes)).append("]\n");
        for (uint32_t i = 0; i < numRoutes; ++i) {
            _routes[i].toConfig(cfg, make_string("%sroute[%d].", prefix.c_str(), i));
        }
    }
}

string
RoutingTableSpec::toString() const
{
    string ret = "";
    toConfig(ret, "");
    return ret;
}

bool
RoutingTableSpec::operator==(const RoutingTableSpec &rhs) const
{
    if (_protocol != rhs._protocol) {
        return false;
    }
    if (_hops.size() != rhs._hops.size()) {
        return false;
    }
    for (uint32_t i = 0, len = _hops.size(); i < len; ++i) {
        if (_hops[i] != rhs._hops[i]) {
            return false;
        }
    }
    if (_routes.size() != rhs._routes.size()) {
        return false;
    }
    for (uint32_t i = 0, len = _routes.size(); i < len; ++i) {
        if (_routes[i] != rhs._routes[i]) {
            return false;
        }
    }
    return true;
}


} // namespace mbus
