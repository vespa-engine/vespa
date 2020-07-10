// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentlocations.h"
#include "geo_location_spec.h"
#include <string>
#include <vespa/vespalib/geo/zcurve.h>

#include <vespa/vespalib/stllike/string.h>

namespace search::common {

class Location : public DocumentLocations,
                 public GeoLocationSpec
{
public:
    Location();
    Location(const GeoLocationSpec& other);
    ~Location() {}
    Location(Location &&) = default;
    bool getRankOnDistance() const { return hasPoint(); }
    bool getPruneOnDistance() const { return hasBoundingBox(); }
    bool getzFailBoundingBoxTest(int64_t docxy) const {
        return _zBoundingBox.getzFailBoundingBoxTest(docxy);
    }
    void setSpec(const GeoLocationSpec& other);
private:
    vespalib::geo::ZCurve::BoundingBox _zBoundingBox;
};

}
