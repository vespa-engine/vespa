// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using AxisName = vespalib::string;
using CoordinateName = vespalib::string;

struct PointName : public std::map<AxisName, CoordinateName>
{
public:
    using BackingMap = std::map<AxisName, CoordinateName>;
    bool operator< (const PointName &other) const;
    PointName bind(AxisName name, CoordinateName value) const;
    PointName() {}
    ~PointName() {}
    PointName(BackingMap &&from);
};

} // namespace vespalib::metrics
} // namespace vespalib
