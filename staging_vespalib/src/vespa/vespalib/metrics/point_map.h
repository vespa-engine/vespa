// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include "dimension.h"
#include "label.h"

namespace vespalib {
namespace metrics {

// internal
class PointMap {
public:
    using BackingMap = std::map<Dimension, Label>;
private:
    const PointMap::BackingMap _map;
    size_t _hash;
public:
    PointMap() : _map(), _hash(0) {}
    PointMap(BackingMap &&from);
    bool operator< (const PointMap &other) const;

    const BackingMap &backingMap() const { return _map; }
};

} // namespace vespalib::metrics
} // namespace vespalib
