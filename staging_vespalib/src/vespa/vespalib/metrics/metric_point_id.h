// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metric_name.h"
#include "point.h"
#include <functional>

namespace vespalib {
namespace metrics {

/** class to use as a map key, identifying a metric and a point */
struct MetricPointId {
    const MetricName _name;
    const Point _point;

    MetricPointId() = delete;

    MetricPointId(MetricName name, Point point)
        : _name(name), _point(point) {}

    bool operator< (const MetricPointId &other) const {
        if (_name != other._name) {
            return _name < other._name;
        }
        return _point < other._point;
    }
    bool operator== (const MetricPointId &other) const {
        return (_name == other._name &&
                _point == other._point);
    }

    MetricName name() const { return _name; }
    Point point() const { return _point; }

};

} // namespace vespalib::metrics
} // namespace vespalib
