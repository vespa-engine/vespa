// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"

#include <vespa/vespalib/datastore/entry_comparator.h>
#include <vespa/vespalib/datastore/unique_store_comparator.h>

namespace search {

/**
 * Less-than comparator used for comparing values of type EntryT stored in an enum store.
 */
template <typename EntryT>
class EnumStoreComparator : public vespalib::datastore::UniqueStoreComparator<EntryT, IEnumStore::InternalIndex> {
public:
    using ParentType = vespalib::datastore::UniqueStoreComparator<EntryT, IEnumStore::InternalIndex>;
    using DataStoreType = typename ParentType::DataStoreType;

    explicit EnumStoreComparator(const DataStoreType& data_store) noexcept : ParentType(data_store) {}

private:
    EnumStoreComparator(const DataStoreType& data_store, const EntryT& lookup_value) noexcept
        : ParentType(data_store, lookup_value) {}

public:
    static bool equal_helper(const EntryT& lhs, const EntryT& rhs) noexcept;

    EnumStoreComparator<EntryT> make_for_lookup(const EntryT& lookup_value) const noexcept {
        return {this->_store, lookup_value};
    }
};

extern template class EnumStoreComparator<int8_t>;
extern template class EnumStoreComparator<int16_t>;
extern template class EnumStoreComparator<int32_t>;
extern template class EnumStoreComparator<int64_t>;
extern template class EnumStoreComparator<float>;
extern template class EnumStoreComparator<double>;

} // namespace search
