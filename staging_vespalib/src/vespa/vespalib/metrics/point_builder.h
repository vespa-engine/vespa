// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

#include "point.h"
#include "point_map.h"

namespace vespalib {
namespace metrics {

class MetricsManager;

class PointBuilder {
private:
    std::shared_ptr<MetricsManager> _owner;
    PointMapBacking _map;

public:
    PointBuilder(std::shared_ptr<MetricsManager> m);
    PointBuilder(std::shared_ptr<MetricsManager> m, const PointMapBacking &from);
    ~PointBuilder() {}

    PointBuilder &&bind(Axis axis, Coordinate coord) &&;
    PointBuilder &&bind(Axis axis, CoordinateValue coord) &&;
    PointBuilder &&bind(AxisName axis, CoordinateValue coord) &&;

    Point build();
    operator Point () &&;
};

} // namespace vespalib::metrics
} // namespace vespalib
