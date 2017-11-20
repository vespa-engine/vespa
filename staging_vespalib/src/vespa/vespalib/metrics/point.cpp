// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"
#include "metrics_collector.h"

#include <vespa/log/log.h>
LOG_SETUP(".point");

namespace vespalib {
namespace metrics {

PointMap::PointMap(PointMapBacking &&from)
    : _map(std::move(from)),
      _hash(0)
{
    for (const PointMapBacking::value_type &entry : _map) {
        _hash = (_hash << 7) + (_hash >> 31) + entry.first.id();
        _hash = (_hash << 7) + (_hash >> 31) + entry.second.id();
    }
}

bool
PointMap::operator< (const PointMap &other) const
{
    // cheap comparison first
    if (_hash < other._hash) return true;
    if (_hash > other._hash) return false;
    auto m = _map.begin();
    auto o = other._map.begin();
    while (m != _map.end()) {
         size_t my_f = m->first.id();
         size_t ot_f = o->first.id();
         if (my_f < ot_f) return true;
         if (my_f > ot_f) return false;

         size_t my_s = m->second.id();
         size_t ot_s = o->second.id();
         if (my_s < ot_s) return true;
         if (my_s > ot_s) return false;

         ++m;
         ++o;
    }
    // equal
    return false;
}



PointBuilder::PointBuilder(std::shared_ptr<MetricsCollector> &&m)
    : _owner(std::move(m)), _map()
{}

PointBuilder::PointBuilder(std::shared_ptr<MetricsCollector> &&m,
                           const PointMapBacking &copyFrom)
    : _owner(std::move(m)), _map(copyFrom)
{
}

PointBuilder &&
PointBuilder::bind(Axis axis, Coordinate coord) &&
{
    _map.erase(axis);
    _map.insert(PointMapBacking::value_type(axis, coord));
    return std::move(*this);
}

PointBuilder &&
PointBuilder::bind(Axis axis, CoordinateValue coord) &&
{
    Coordinate c = _owner->coordinate(coord);
    return std::move(*this).bind(axis, c);
}

PointBuilder &&
PointBuilder::bind(AxisName axis, CoordinateValue coord) &&
{
    Axis a = _owner->axis(axis);
    Coordinate c = _owner->coordinate(coord);
    return std::move(*this).bind(a, c);
}

Point
PointBuilder::build()
{
    return _owner->pointFrom(PointMapBacking(_map));
}

PointBuilder::operator Point() &&
{
    return _owner->pointFrom(std::move(_map));
}


} // namespace vespalib::metrics
} // namespace vespalib
