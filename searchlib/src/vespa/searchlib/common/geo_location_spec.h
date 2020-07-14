// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <cstdint>
#include "geo_location.h"

namespace search::common {

struct GeoLocationSpec
{
public:
    const std::string field_name;
    const GeoLocation location;
};

} // namespace
