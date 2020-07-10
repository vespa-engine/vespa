// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location.h"
#include <limits>

namespace search::common {

Location::Location() : _zBoundingBox(0,0,0,0) {}

bool Location::parse(const std::string &locStr) {
    bool valid = GeoLocationSpec::parseOldFormat(locStr);
    if (valid) {
        _zBoundingBox = vespalib::geo::ZCurve::BoundingBox(getMinX(),
                                                           getMaxX(),
                                                           getMinY(),
                                                           getMaxY());
    }
    return valid;
}

} // namespace
