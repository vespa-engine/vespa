// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metric_name.h"
#include "point.h"
#include <functional>

namespace vespalib {
namespace metrics {

// internal
struct MetricIdentifier {
    const MetricName _name;
    const Point _point;

    MetricIdentifier() = delete;

    MetricIdentifier(MetricName name, Point point)
        : _name(name), _point(point) {}

    bool operator< (const MetricIdentifier &other) const {
        if (_name != other._name) {
            return _name < other._name;
        }
        return _point < other._point;
    }
    bool operator== (const MetricIdentifier &other) const {
        return (_name == other._name &&
                _point == other._point);
    }

    MetricName name() const { return _name; }
    Point point() const { return _point; }

};

} // namespace vespalib::metrics
} // namespace vespalib
