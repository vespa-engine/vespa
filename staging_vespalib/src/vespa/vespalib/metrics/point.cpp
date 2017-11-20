// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"
#include "metrics_collector.h"

namespace vespalib {
namespace metrics {

PointMap::PointMap(std::map<Axis, Coordinate> &&from)
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

PointName::PointName(BackingMap &&from)
    : BackingMap(std::move(from))
{}

bool
PointName::operator< (const PointName &other) const
{
    // do cheap comparisons first:
    if (size() == 0 && other.size() == 0) {
        // equal no-dimensions point
        return false;
    }
    if (size() < other.size()) {
        return true;
    }
    if (size() > other.size()) {
        return false;
    }
    const_iterator m_it = begin();
    const_iterator o_it = other.begin();

    while (m_it != end()) {
        const AxisName & m_axis = m_it->first;
        const AxisName & o_axis = o_it->first;
        if (m_axis < o_axis) { return true; }
        if (o_axis < m_axis) { return false; }

        const CoordinateName & m_coord = m_it->second;
        const CoordinateName & o_coord = o_it->second;
        if (m_coord < o_coord) { return true; }
        if (o_coord < m_coord) { return false; }

        ++m_it;
        ++o_it;
    }
    // everything equal
    return false;
}

PointName
PointName::bind(AxisName name, CoordinateName value) const
{
    std::map<AxisName, CoordinateName> copy = *this;
    copy[name] = value;
    return PointName(std::move(copy));
}


PointBuilder::PointBuilder(std::shared_ptr<MetricsCollector> &&m)
    : _owner(std::move(m)), _map()
{}

PointBuilder &&
PointBuilder::bind(Axis axis, Coordinate coord) &&
{
    _map.insert(PointMapBacking::value_type(axis, coord));
    return std::move(*this);
}

PointBuilder &&
PointBuilder::bind(Axis axis, CoordinateName coord) &&
{
    Coordinate c = _owner->coordinate(coord);
    return std::move(*this).bind(axis, c);
}

PointBuilder &&
PointBuilder::bind(AxisName axis, CoordinateName coord) &&
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
