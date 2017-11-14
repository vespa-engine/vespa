// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "name_collection.h"
#include <assert.h>

namespace vespalib {
namespace metrics {

using Guard = std::lock_guard<std::mutex>;

const vespalib::string &
NameCollection::lookup(int idx) const
{
    size_t id = idx;
    Guard guard(_lock);
    assert(id < _names.size());
    return _names.lookup(id);
}

int
NameCollection::lookup(const vespalib::string& name) const
{
    Guard guard(_lock);
    return _names.lookup(name);
}

int
NameCollection::resolve(const vespalib::string& name)
{
    Guard guard(_lock);
    int id = _names.lookup(name);
    if (id < 0) {
        id = _names.size();
        _names.add(name);
    }
    return id;
}

} // namespace vespalib::metrics
} // namespace vespalib
