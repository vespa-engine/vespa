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

    EnumStoreComparator(const DataStoreType& data_store)
        : ParentType(data_store)
    {}

private:
    EnumStoreComparator(const DataStoreType& data_store, const EntryT& lookup_value)
        : ParentType(data_store, lookup_value)
    {}

public:
    static bool equal_helper(const EntryT& lhs, const EntryT& rhs);

    EnumStoreComparator<EntryT> make_folded() const {
        return *this;
    }
    EnumStoreComparator<EntryT> make_for_lookup(const EntryT& lookup_value) const {
        return EnumStoreComparator<EntryT>(this->_store, lookup_value);
    }
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

private:
    EnumStoreStringComparator(const DataStoreType& data_store, bool fold);

    /**
     * Creates a comparator using the given low-level data store and that uses the
     * given value during compare if the enum index is invalid.
     */
    EnumStoreStringComparator(const DataStoreType& data_store, bool fold, const char* lookup_value);
    EnumStoreStringComparator(const DataStoreType& data_store, bool fold, const char* lookup_value, bool prefix);

public:
    bool less(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override;
    bool equal(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override;
    EnumStoreStringComparator make_folded() const {
        return EnumStoreStringComparator(_store, true);
    }
    EnumStoreStringComparator make_for_lookup(const char* lookup_value) const {
        return EnumStoreStringComparator(_store, _fold, lookup_value);
    }
    EnumStoreStringComparator make_for_prefix_lookup(const char* lookup_value) const {
        return EnumStoreStringComparator(_store, _fold, lookup_value, true);
    }
private:
    inline bool use_prefix() const noexcept { return _prefix; }
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
