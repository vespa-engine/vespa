// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>

namespace vespalib {
namespace metrics {

class Point {
private:
    const size_t _point_idx;
public:
    size_t id() const { return _point_idx; }

    static Point empty;

    explicit Point(size_t id) : _point_idx(id) {}
};

} // namespace vespalib::metrics
} // namespace vespalib
