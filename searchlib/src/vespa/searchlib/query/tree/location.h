// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vespa/searchlib/common/geo_location_spec.h>
#include "point.h"
#include "rectangle.h"

namespace vespalib { class asciistream; }
namespace search::query {

class Location : public search::common::GeoLocation {
    using Parent = search::common::GeoLocation;
public:
    Location() {}
    Location(const Parent &spec) : Parent(spec) {}
    ~Location() {}
    Location(const Point &p, uint32_t dist, uint32_t x_asp);
    Location(const Rectangle &rect);
    Location(const Rectangle &rect, const Point &p, uint32_t dist, uint32_t x_asp);

    bool operator==(const Location &other) const;
    std::string getOldFormatString() const;
    std::string getJsonFormatString() const;
};

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Location &loc);

}
