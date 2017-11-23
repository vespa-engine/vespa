// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
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

} // namespace vespalib::metrics
} // namespace vespalib
