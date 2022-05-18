// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "handle.h"
#include "point_map.h"

namespace vespalib::metrics {

/**
 * Opaque handle representing an unique N-dimensional point
 **/
class Point : public Handle<Point> {
public:
    static Point empty;
    explicit Point(size_t id) : Handle<Point>(id) {}

    static Point from_map(const PointMap& map);
    static Point from_map(PointMap&& map);
    const PointMap& as_map() const;
};

} // namespace vespalib::metrics
