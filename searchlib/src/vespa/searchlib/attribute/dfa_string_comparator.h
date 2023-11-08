// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>
#include <functional>

namespace search::attribute {

/**
 * Less-than comparator used for comparing next candidate string
 * (successor) from vespa::fuzzy::LevenshteinDfa with strings stored
 * in an enum store as part of a dictionary iterator seek, skipping
 * entries that don't match the fuzzy term.
 *
 * The code points from the candidate string are not folded during
 * the comparison.
 */

class DfaStringComparator : public vespalib::datastore::UniqueStoreStringComparator<IEnumStore::InternalIndex>
{
public:
    using ParentType = vespalib::datastore::UniqueStoreStringComparator<IEnumStore::InternalIndex>;
    using DataStoreType = ParentType::DataStoreType;
private:
    using ParentType::get;
    std::reference_wrapper<const std::vector<uint32_t>> _candidate;
    bool _cased;

public:
    DfaStringComparator(const DataStoreType& data_store, const std::vector<uint32_t>& candidate, bool cased);

    bool less(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override;
};

}
