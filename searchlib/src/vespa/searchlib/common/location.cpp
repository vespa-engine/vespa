// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location.h"
#include <limits>

namespace search::common {

Location::Location() : _zBoundingBox(0,0,0,0) {}

Location::Location(const GeoLocationSpec &other)
  : Location()
{
    setSpec(other);
}

void
Location::setSpec(const GeoLocationSpec &other)
{
    using vespalib::geo::ZCurve;

    GeoLocationSpec::operator=(other);
    if (isValid()) {
        _zBoundingBox = ZCurve::BoundingBox(getMinX(), getMaxX(),
                                            getMinY(), getMaxY());
    }
}

} // namespace
