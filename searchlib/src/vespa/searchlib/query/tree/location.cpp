// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location.h"
#include "point.h"
#include "rectangle.h"
#include <vespa/vespalib/stllike/asciistream.h>

using vespalib::asciistream;
using search::common::GeoLocation;

namespace search::query {

static GeoLocation::Box convert(const Rectangle &rect) {
    GeoLocation::Range x_range{rect.left, rect.right};
    GeoLocation::Range y_range{rect.top, rect.bottom};
    return GeoLocation::Box{x_range, y_range};
}

Location::Location(const Point &p, uint32_t max_dist, uint32_t aspect)
    : Parent(p, max_dist, GeoLocation::Aspect(aspect))
{}

Location::Location(const Rectangle &rect,
                   const Point &p, uint32_t max_dist, uint32_t aspect)
    : Parent(convert(rect), p, max_dist, GeoLocation::Aspect(aspect))
{}


Location::Location(const Rectangle &rect)
    : Parent(convert(rect))
{}

bool
Location::operator==(const Location &other) const
{
    auto me = getDebugString();
    auto it = other.getDebugString();
    if (me == it) {
        return true;
    } else {
        // dump 'me' and 'it' here if unit tests fail
        return false;
    }
}

std::string
Location::getDebugString() const
{
    vespalib::asciistream buf;
    buf << "query::Location{";
    if (has_point) {
        buf << "point=[" << point.x << "," << point.y << "]";
        if (has_radius()) {
            buf << ",radius=" << radius;
        }
        if (x_aspect.active()) {
            buf << ",x_aspect=" << x_aspect.multiplier;
        }
    }
    if (bounding_box.active()) {
        if (has_point) buf << ",";
        buf << "bb.x=[" << bounding_box.x.lo << "," << bounding_box.x.hi << "],";
        buf << "bb.y=[" << bounding_box.y.lo << "," << bounding_box.y.hi << "]";
    }
    buf << "}";
    return buf.str();
}

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Location &loc) {
    return out << loc.getDebugString();
}

}
