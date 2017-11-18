// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <memory>
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

class MetricsCollector;

class Axis {
    const size_t _axis_idx;
public:
    size_t id() const { return _axis_idx; }
    Axis(size_t id) : _axis_idx(id) {}
    bool operator< (const Axis &other) const { return id() < other.id(); }
};

class Coordinate {
    const size_t _coord_idx;
public:
    size_t id() const { return _coord_idx; }
    Coordinate(size_t id) : _coord_idx(id) {}
};

using PointMap = std::map<Axis, Coordinate>;

class Point {
private:
    std::shared_ptr<MetricsCollector> _owner;
    const size_t _point_idx;
public:
    size_t id() const { return _point_idx; }
    Point bind(Axis axis, Coordinate coord) const;
    Point bind(Axis axis, CoordinateName coord) const;
    Point bind(AxisName axis, CoordinateName coord) const;
    Point(std::shared_ptr<MetricsCollector> m, size_t id)
        : _owner(m), _point_idx(id)
    {}
};

} // namespace vespalib::metrics
} // namespace vespalib
