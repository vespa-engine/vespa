// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "handle.h"

namespace vespalib::metrics {

/**
 * Opaque handle representing an unique N-dimensional point
 **/
class Point : public Handle<Point> {
public:
    static Point empty;
    explicit Point(size_t id) : Handle<Point>(id) {}
};

} // namespace vespalib::metrics
