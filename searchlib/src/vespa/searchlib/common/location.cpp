// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location.h"
#include <limits>

namespace search::common {

Location::Location() : _zBoundingBox(0,0,0,0) {}

Location::Location(const GeoLocationSpec &other)
  : GeoLocationSpec(other),
    _zBoundingBox(0,0,0,0)
{
    using vespalib::geo::ZCurve;
    if (isValid()) {
        _zBoundingBox = ZCurve::BoundingBox(getMinX(), getMaxX(),
                                            getMinY(), getMaxY());
    }
}

} // namespace
