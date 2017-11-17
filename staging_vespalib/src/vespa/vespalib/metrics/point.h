// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using AxisName = vespalib::string;
using Coordinate = vespalib::string;

struct Point : public std::map<AxisName, Coordinate>
{
public:
    using BackingMap = std::map<AxisName, Coordinate>;
    bool operator< (const Point &other) const;
    Point bind(AxisName name, Coordinate value) const;
    Point() {}
    ~Point() {}
    Point(BackingMap &&from);
};

} // namespace vespalib::metrics
} // namespace vespalib
