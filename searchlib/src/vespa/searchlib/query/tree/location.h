// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vespa/searchlib/common/geo_location_spec.h>

namespace vespalib { class asciistream; }
namespace search::query {

// for unit tests:
struct Point;
struct Rectangle;

class Location : public search::common::GeoLocationSpec {
    using Parent = search::common::GeoLocationSpec;
public:
    Location() {}
    Location(const Parent &spec) : Parent(spec) {}
    ~Location() {}
    Location(const Point &p, uint32_t dist, uint32_t x_asp);
    Location(const Rectangle &rect);
    Location(const Rectangle &rect, const Point &p, uint32_t dist, uint32_t x_asp);

    bool operator==(const Location &other) const;
    std::string getDebugString() const {
        return getOldFormatLocationStringWithField();
    }
};

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Location &loc);

}
