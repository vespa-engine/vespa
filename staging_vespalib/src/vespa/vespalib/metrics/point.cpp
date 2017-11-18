// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"

namespace vespalib {
namespace metrics {

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


} // namespace vespalib::metrics
} // namespace vespalib
