// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "name_collection.h"

#include <vespa/check_require.h>

namespace vespalib::metrics {

using Guard = std::lock_guard<std::mutex>;

NameCollection::NameCollection() {
    size_t first = resolve("");
    CHECK(first == 0);
    CHECK(lookup(first) == "");
    CHECK(_names_by_id.size() == 1);
    CHECK(_names.size() == 1);
    (void)first; // in case of NOP asserts
}

NameCollection::~NameCollection() = default;

const std::string& NameCollection::lookup(size_t id) const {
    Guard guard(_lock);
    CHECK(id < _names_by_id.size());
    return _names_by_id[id]->first;
}

size_t NameCollection::resolve(std::string_view name) {
    Guard  guard(_lock);
    size_t nextId = _names_by_id.size();
    auto   iter_check = _names.emplace(name, nextId);
    if (iter_check.second) {
        _names_by_id.emplace_back(iter_check.first);
    }
    return iter_check.first->second;
}

size_t NameCollection::size() const {
    Guard guard(_lock);
    return _names_by_id.size();
}

} // namespace vespalib::metrics
