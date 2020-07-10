// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location.h"
#include "point.h"
#include "rectangle.h"
#include <vespa/vespalib/stllike/asciistream.h>

using vespalib::asciistream;

namespace search::query {

Location::Location(const Point &point, uint32_t max_dist, uint32_t x_aspect) {
    _x = point.x;
    _y = point.y;
    _has_point = true;
    _radius = max_dist;
    _has_radius = true;
    _x_aspect = x_aspect;
    _valid = true;
    adjust_bounding_box();
}

Location::Location(const Rectangle &rect,
                   const Point &point, uint32_t max_dist, uint32_t x_aspect)
{
    _x = point.x;
    _y = point.y;
    _has_point = true;

    _radius = max_dist;
    _has_radius = true;

    _x_aspect = x_aspect;

    _min_x = rect.left;
    _min_y = rect.top;
    _max_x = rect.right;
    _max_y = rect.bottom;
    _has_bounding_box = true;
    
    _valid = true;
}


Location::Location(const Rectangle &rect) {
    _min_x = rect.left;
    _min_y = rect.top;
    _max_x = rect.right;
    _max_y = rect.bottom;
    _has_bounding_box = true;
    
    _valid = true;
}

bool
Location::operator==(const Location &other) const
{
    auto me = getOldFormatLocationStringWithField();
    auto it = other.getOldFormatLocationStringWithField();
    if (me == it) {
        return true;
    } else {
        // dump 'me' and 'it' here if unit tests fail
        return false;
    }
}


vespalib::asciistream &operator<<(vespalib::asciistream &out, const Location &loc) {
    return out << loc.getOldFormatLocationString();
}

}
