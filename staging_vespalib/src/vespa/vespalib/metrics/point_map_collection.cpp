// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "point_map_collection.h"
#include <assert.h>

namespace vespalib {
namespace metrics {

using Guard = std::lock_guard<std::mutex>;

const HashedPointMap &
PointMapCollection::lookup(size_t id) const
{
    Guard guard(_lock);
    assert(id < _vec.size());
    PointMapMap::const_iterator iter = _vec[id];
    return iter->first;
}

size_t
PointMapCollection::resolve(HashedPointMap map)
{
    Guard guard(_lock);
    size_t nextId = _vec.size();
    auto iter_check = _map.emplace(std::move(map), nextId);
    if (iter_check.second) {
        _vec.push_back(iter_check.first);
    }
    return iter_check.first->second;
}

size_t
PointMapCollection::size() const
{
    Guard guard(_lock);
    return _vec.size();
}

} // namespace vespalib::metrics
} // namespace vespalib
