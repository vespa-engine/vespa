// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentlocations.h"
#include "geo_location.h"

namespace search::common {

class Location : public DocumentLocations,
                 public GeoLocation
{
public:
    Location(const GeoLocation& from);
    ~Location() {}
    Location(Location &&) = default;
    bool getRankOnDistance() const { return has_point; }
    bool getPruneOnDistance() const { return can_limit(); }
};

}
