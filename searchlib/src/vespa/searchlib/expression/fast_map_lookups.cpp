// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_map_lookups.h"

#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::expression {

FastMapLookups::FastMapLookups() = default;

FastMapLookups::FastMapLookups(const std::vector<FastMapLookup>& lookups) : _lookups() {
    for (const auto& lookup : lookups) {
        _lookups[lookup.name()] = lookup;
    }
}

FastMapLookups::~FastMapLookups() = default;

const FastMapLookup* FastMapLookups::find(const std::string& name) const noexcept {
    auto it = _lookups.find(name);
    return (it != _lookups.end()) ? &it->second : nullptr;
}

} // namespace search::expression
