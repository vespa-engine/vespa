// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routingspec.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

RouteSpec::RouteSpec(const string &name) noexcept :
    _name(name),
    _hops()
{ }

RouteSpec::RouteSpec(const RouteSpec &) = default;
RouteSpec & RouteSpec::operator = (const RouteSpec &) = default;
RouteSpec::RouteSpec(RouteSpec &&) noexcept = default;
RouteSpec & RouteSpec::operator = (RouteSpec &&) noexcept = default;

RouteSpec::~RouteSpec() = default;

RouteSpec &
RouteSpec::addHop(const string &hop) & {
    _hops.push_back(hop);
    return *this;
}

RouteSpec &&
RouteSpec::addHop(const string &hop) && {
    _hops.push_back(hop);
    return std::move(*this);
}

RouteSpec &
RouteSpec::setHop(uint32_t i, const string &hop) {
    _hops[i] = hop;
    return *this;
}

void
RouteSpec::toConfig(string &cfg, const string &prefix) const
{
    cfg.append(prefix).append("name ").append(RoutingSpec::toConfigString(_name)).append("\n");
    uint32_t numHops = _hops.size();
    if (numHops > 0) {
        cfg.append(prefix).append("hop[").append(make_string("%d", numHops)).append("]\n");
        for (uint32_t i = 0; i < numHops; ++i) {
            cfg.append(prefix).append("hop[").append(make_string("%d", i)).append("] ");
            cfg.append(RoutingSpec::toConfigString(_hops[i])).append("\n");
        }
    }
}

string
RouteSpec::toString() const
{
    string ret = "";
    toConfig(ret, "");
    return ret;
}

bool
RouteSpec::operator==(const RouteSpec &rhs) const
{
    if (_name != rhs._name) {
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
    return true;
}

} // namespace mbus
