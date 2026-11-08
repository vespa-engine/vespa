// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_map_lookups.h"

namespace search::grouping {

FastMapLookups::FastMapLookups() noexcept = default;

FastMapLookups::FastMapLookups(std::vector<FastMapLookup> lookups) noexcept : _lookups(std::move(lookups)) {}

FastMapLookups::~FastMapLookups() = default;

const FastMapLookup* FastMapLookups::find(std::string_view lookup_attribute) const noexcept {
    for (const auto& lookup : _lookups) {
        const auto& field = lookup.field_name;
        const auto& name = lookup.lookup_name;
        if (lookup_attribute.size() == field.size() + 1 + name.size() && lookup_attribute.starts_with(field) &&
            lookup_attribute[field.size()] == '$' && lookup_attribute.ends_with(name)) {
            return &lookup;
        }
    }
    return nullptr;
}

} // namespace search::grouping
