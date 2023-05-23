// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include <vespa/vespalib/datastore/entry_comparator.h>
#include <vespa/vespalib/datastore/unique_store_comparator.h>
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>

namespace search {

/**
 * Less-than comparator used for comparing values of type EntryT stored in an enum store.
 */
template <typename EntryT>
class EnumStoreComparator : public vespalib::datastore::UniqueStoreComparator<EntryT, IEnumStore::InternalIndex> {
public:
    using ParentType = vespalib::datastore::UniqueStoreComparator<EntryT, IEnumStore::InternalIndex>;
    using DataStoreType = typename ParentType::DataStoreType;

    EnumStoreComparator(const DataStoreType& data_store, const EntryT& fallback_value)
        : ParentType(data_store, fallback_value)
    {}
    EnumStoreComparator(const DataStoreType& data_store)
        : ParentType(data_store)
    {}

    static bool equal_helper(const EntryT& lhs, const EntryT& rhs);
};

/**
 * Less-than comparator used for comparing strings stored in an enum store.
 *
 * The input string values are first folded, then compared.
 * If they are equal, then it falls back to comparing without folding.
 */
class EnumStoreStringComparator : public vespalib::datastore::UniqueStoreStringComparator<IEnumStore::InternalIndex> {
protected:
    using ParentType = vespalib::datastore::UniqueStoreStringComparator<IEnumStore::InternalIndex>;
    using DataStoreType = ParentType::DataStoreType;
private:
    using ParentType::get;

public:
    EnumStoreStringComparator(const DataStoreType& data_store)
        : EnumStoreStringComparator(data_store, false)
    {}
    EnumStoreStringComparator(const DataStoreType& data_store, bool fold);

    /**
     * Creates a comparator using the given low-level data store and that uses the
     * given value during compare if the enum index is invalid.
     */
    EnumStoreStringComparator(const DataStoreType& data_store, const char* fallback_value)
        : EnumStoreStringComparator(data_store, false, fallback_value)
    {}
    EnumStoreStringComparator(const DataStoreType& data_store, bool fold, const char* fallback_value);
    EnumStoreStringComparator(const DataStoreType& data_store, bool fold, const char* fallback_value, bool prefix);

    bool less(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override;
    bool equal(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override;
private:
    inline bool use_prefix() const { return _prefix; }
    const bool _fold;
    const bool _prefix;
    uint32_t   _prefix_len;
};

extern template class EnumStoreComparator<int8_t>;
extern template class EnumStoreComparator<int16_t>;
extern template class EnumStoreComparator<int32_t>;
extern template class EnumStoreComparator<int64_t>;
extern template class EnumStoreComparator<float>;
extern template class EnumStoreComparator<double>;

}

