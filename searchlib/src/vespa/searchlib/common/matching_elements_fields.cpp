// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matching_elements_fields.h"

namespace search {

MatchingElementsFields::MatchingElementsFields() = default;
MatchingElementsFields::MatchingElementsFields(const MatchingElementsFields&) = default;
MatchingElementsFields::MatchingElementsFields(MatchingElementsFields&&) noexcept = default;
MatchingElementsFields::~MatchingElementsFields() = default;

void
MatchingElementsFields::merge(const MatchingElementsFields& rhs)
{
    for (auto& field : rhs._fields) {
        _fields.insert(field);
    }
    for (auto& kv : rhs._struct_fields) {
        _struct_fields[kv.first] = kv.second;
    }
}

} // namespace search
