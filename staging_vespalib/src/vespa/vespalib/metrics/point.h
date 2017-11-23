// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <memory>
#include <vespa/vespalib/stllike/string.h>

#include "axis.h"
#include "coordinate.h"

namespace vespalib {
namespace metrics {

using PointMapBacking = std::map<Axis, Coordinate>;

class PointMap {
private:
    const PointMapBacking _map;
    size_t _hash;
public:
    PointMap() : _map(), _hash(0) {}
    PointMap(PointMapBacking &&from);
    bool operator< (const PointMap &other) const;

    const PointMapBacking &backing() const { return _map; }
};

class Point {
private:
    const size_t _point_idx;
public:
    size_t id() const { return _point_idx; }

    explicit Point(size_t id) : _point_idx(id) {}
};

class MetricsManager;

class PointBuilder {
private:
    std::shared_ptr<MetricsManager> _owner;
    PointMapBacking _map;

public:
    PointBuilder(std::shared_ptr<MetricsManager> &&m);
    PointBuilder(std::shared_ptr<MetricsManager> &&m, const PointMapBacking &from);
    ~PointBuilder() {}

    PointBuilder &&bind(Axis axis, Coordinate coord) &&;
    PointBuilder &&bind(Axis axis, CoordinateValue coord) &&;
    PointBuilder &&bind(AxisName axis, CoordinateValue coord) &&;

    Point build();
    operator Point () &&;
};


} // namespace vespalib::metrics
} // namespace vespalib
