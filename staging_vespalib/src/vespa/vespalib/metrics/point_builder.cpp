// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"
#include "metrics_manager.h"

namespace vespalib {
namespace metrics {

PointBuilder::PointBuilder(std::shared_ptr<MetricsManager> m)
    : _owner(std::move(m)), _map()
{}

PointBuilder::PointBuilder(std::shared_ptr<MetricsManager> m,
                           const PointMapBacking &copyFrom)
    : _owner(std::move(m)), _map(copyFrom)
{}

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
