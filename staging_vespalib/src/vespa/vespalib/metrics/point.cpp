// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"
#include "metrics_collector.h"

namespace vespalib {
namespace metrics {

PointMap::PointMap(std::map<Axis, Coordinate> &&from)
    : _map(std::move(from)),
      _hash(0)
{
    auto it = _map.begin();
    while (it != _map.end()) {
        _hash = (_hash << 7) + (_hash >> 31) + it->first.id();
        _hash = (_hash << 7) + (_hash >> 31) + it->second.id();
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


Point
Point::bind(Axis axis, Coordinate coord) const
{
    return _owner->bind(*this, axis, coord);
}

Point
Point::bind(Axis axis, CoordinateName coord) const
{
    Coordinate c = _owner->coordinate(coord);
    return bind(axis, c);
}

Point
Point::bind(AxisName axis, CoordinateName coord) const
{
    Axis a = _owner->axis(axis);
    Coordinate c = _owner->coordinate(coord);
    return bind(a, c);
}


} // namespace vespalib::metrics
} // namespace vespalib
