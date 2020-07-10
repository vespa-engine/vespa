// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib { class asciistream; }
namespace search::query {

// for unit tests:
struct Point;
struct Rectangle;

class Location {
    vespalib::string _location_string;

public:
    Location() : _location_string() {}
    Location(const Point &p, uint32_t dist, uint32_t x_asp);
    Location(const Rectangle &rect);
    Location(const Rectangle &rect, const Point &p, uint32_t dist, uint32_t x_asp);
    Location(const vespalib::string &s) : _location_string(s) {}

    bool operator==(const Location &other) const {
        return _location_string == other._location_string;
    }
    const vespalib::string &getLocationString() const {
        return _location_string;
    }
};

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Location &loc);

}
