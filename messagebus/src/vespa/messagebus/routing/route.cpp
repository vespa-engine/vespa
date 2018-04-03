// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "route.h"
#include "routeparser.h"

namespace mbus {

Route::Route() :
    _hops()
{ }

Route::Route(std::vector<Hop> lst) :
    _hops(std::move(lst))
{ }

Route::~Route() { }

Route &
Route::addHop(Hop hop)
{
    _hops.emplace_back(std::move(hop));
    return *this;
}

Route &
Route::setHop(uint32_t i, Hop hop)
{
    _hops[i] = std::move(hop);
    return *this;
}

Hop
Route::removeHop(uint32_t i)
{
    Hop ret = std::move(_hops[i]);
    _hops.erase(_hops.begin() + i);
    return ret;
}

Route &
Route::clearHops()
{
    _hops.clear();
    return *this;
}

string
Route::toString() const {
    string ret = "";
    for (uint32_t i = 0; i < _hops.size(); ++i) {
        ret.append(_hops[i].toString());
        if (i < _hops.size() - 1) {
            ret.append(" ");
        }
    }
    return ret;
}

string
Route::toDebugString() const {
    string ret = "Route(hops = { ";
    for (uint32_t i = 0; i < _hops.size(); ++i) {
        ret.append(_hops[i].toDebugString());
        if (i < _hops.size() - 1) {
            ret.append(", ");
        }
    }
    ret.append(" })");
    return ret;
}

Route
Route::parse(vespalib::stringref route)
{
    return RouteParser::createRoute(route);
}

} // namespace mbus
