// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point.h"
#include "metrics_manager.h"

namespace vespalib {
namespace metrics {

HashedPointMap::HashedPointMap(PointMap &&from)
    : _map(std::move(from)),
      _hash(0)
{
    for (const PointMap::value_type &entry : _map) {
        _hash = (_hash << 7) + (_hash >> 31) + entry.first.id();
        _hash = (_hash << 7) + (_hash >> 31) + entry.second.id();
    }
}

bool
HashedPointMap::operator< (const HashedPointMap &other) const
{
    // cheap comparison first
    if (_hash != other._hash) {
        return _hash < other._hash;
    }
    if (_map.size() != other._map.size()) {
        return _map.size() < other._map.size();
    }
    // sizes equal, iterate in parallel
    for (auto m = _map.begin(), o = other._map.begin();
         m != _map.end();
         ++m, ++o)
    {
        const Dimension& d1 = m->first;
        const Dimension& d2 = o->first;
        if (d1 != d2) {
            return d1 < d2;
        }
        const Label &l1 = m->second;
        const Label &l2 = o->second;
        if (l1 != l2) {
            return l1 < l2;
        }
    }
    // equal
    return false;
}

} // namespace vespalib::metrics
} // namespace vespalib
