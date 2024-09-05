// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "geo_location.h"
#include <cstdint>
#include <string>

namespace search::common {

/**
 * Immutable specification of a geo-location query item.
 **/
struct GeoLocationSpec
{
public:
    const std::string field_name;
    const GeoLocation location;
};

} // namespace
