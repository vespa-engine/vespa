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
    bool getRankOnDistance() const { return hasPoint(); }
    bool getPruneOnDistance() const { return hasBoundingBox(); }
    bool getzFailBoundingBoxTest(int64_t docxy) const {
        return _zBoundingBox.getzFailBoundingBoxTest(docxy);
    }
    bool parse(const std::string &locStr);
private:
    GeoLocationSpec _spec;
    vespalib::geo::ZCurve::BoundingBox _zBoundingBox;
};

}
