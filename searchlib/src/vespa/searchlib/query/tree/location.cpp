// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    auto me = getJsonFormatString();
    auto it = other.getJsonFormatString();
    if (me == it) {
        return true;
    } else {
        // dump 'me' and 'it' here if unit tests fail
        // fprintf(stderr, "me='%s', it='%s'\n", me.c_str(), it.c_str());
        return false;
    }
}

std::string
Location::getOldFormatString() const
{
    // we need to product what search::common::GeoLocationParser can parse
    vespalib::asciistream buf;
    if (has_point) {
        buf << "(2"  // dimensionality
                        << "," << point.x
                        << "," << point.y
                        << "," << radius
                        << "," << "0"  // table id.
                        << "," << "1"  // rank multiplier.
                        << "," << "0" // rank only on distance.
                        << "," << x_aspect.multiplier // aspect multiplier
                        << ")";
    }
    if (bounding_box.active()) {
        buf << "[2," << bounding_box.x.low
            << "," << bounding_box.y.low
            << "," << bounding_box.x.high
            << "," << bounding_box.y.high
            << "]" ;
    }
    return buf.str();
}

std::string
Location::getJsonFormatString() const
{
    // Only produce what search::common::GeoLocationParser can parse
    vespalib::asciistream buf;
    buf << "{";
    if (has_point) {
        buf << "p:{x:" << point.x << ",y:" << point.y << "}";
        if (has_radius()) {
            buf << "," << "r:" << radius;
        }
        if (x_aspect.active()) {
            buf << "," << "a:" << x_aspect.multiplier;
        }
    }
    if (bounding_box.active()) {
        if (has_point) {
            buf << ",";
        }
        buf << "b:{x:[" << bounding_box.x.low
            << "," << bounding_box.x.high
            << "],y:[" << bounding_box.y.low
            << "," << bounding_box.y.high
            << "]}" ;
    }
    buf << "}";
    return buf.str();
}

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Location &loc) {
    return out << loc.getJsonFormatString();
}

} // namespace
