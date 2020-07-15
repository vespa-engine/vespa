// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location.h"
#include "point.h"
#include "rectangle.h"
#include <vespa/vespalib/stllike/asciistream.h>

using vespalib::asciistream;

namespace search::query {

Location::Location(const Point &point, uint32_t max_dist, uint32_t x_aspect) {
    asciistream loc;
    loc << "(2"  // dimensionality
        << "," << point.x
        << "," << point.y
        << "," << max_dist
        << "," << "0"  // table id.
        << "," << "1"  // rank multiplier.
        << "," << "0"  // rank only on distance.
        << "," << x_aspect  // x aspect.
        << ")";
    _location_string = loc.str();
}

Location::Location(const Rectangle &rect,
                   const Point &point, uint32_t max_dist, uint32_t x_aspect)
{
    asciistream loc;
    loc << "(2"  // dimensionality
        << "," << point.x
        << "," << point.y
        << "," << max_dist
        << "," << "0"  // table id.
        << "," << "1"  // rank multiplier.
        << "," << "0"  // rank only on distance.
        << "," << x_aspect  // x aspect.
        << ")";
    loc << "[2," << rect.left
        << "," << rect.top
        << "," << rect.right
        << "," << rect.bottom
        << "]" ;
    _location_string = loc.str();

}


Location::Location(const Rectangle &rect) {
    asciistream loc;
    loc << "[2," << rect.left
        << "," << rect.top
        << "," << rect.right
        << "," << rect.bottom
        << "]" ;
    _location_string = loc.str();
}

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Location &loc) {
    return out << loc.getLocationString();
}

}
