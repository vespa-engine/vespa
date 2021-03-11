// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    EnumStoreComparator(const DataStoreType& data_store, const EntryT& fallback_value, bool prefix = false);
    EnumStoreComparator(const DataStoreType& data_store);

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
    using ParentType::get;

    static int compare(const char* lhs, const char* rhs);

public:
    EnumStoreStringComparator(const DataStoreType& data_store);

    /**
     * Creates a comparator using the given low-level data store and that uses the
     * given value during compare if the enum index is invalid.
     */
    EnumStoreStringComparator(const DataStoreType& data_store, const char* fallback_value);

    static bool equal(const char* lhs, const char* rhs) {
        return compare(lhs, rhs) == 0;
    }

    bool less(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override {
        return compare(get(lhs), get(rhs)) < 0;
    }
    bool equal(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override {
        return compare(get(lhs), get(rhs)) == 0;
    }
};


/**
 * Less-than comparator used for folded-only comparing strings stored in an enum store.
 *
 * The input string values are first folded, then compared.
 * There is NO fallback if they are equal.
 */
class EnumStoreFoldedStringComparator : public EnumStoreStringComparator {
private:
    using ParentType = EnumStoreStringComparator;

    bool   _prefix;
    size_t _prefix_len;

    inline bool use_prefix() const { return _prefix; }
    static int compare_folded(const char* lhs, const char* rhs);
    static int compare_folded_prefix(const char* lhs, const char* rhs, size_t prefix_len);

public:
    /**
     * Creates a comparator using the given low-level data store.
     *
     * @param prefix whether we should perform prefix compare.
     */
    EnumStoreFoldedStringComparator(const DataStoreType& data_store, bool prefix = false);

    /**
     * Creates a comparator using the given low-level data store and that uses the
     * given value during compare if the enum index is invalid.
     *
     * @param prefix whether we should perform prefix compare.
     */
    EnumStoreFoldedStringComparator(const DataStoreType& data_store,
                                    const char* fallback_value, bool prefix = false);

    static bool equal(const char* lhs, const char* rhs) {
        return compare_folded(lhs, rhs) == 0;
    }

    bool less(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override {
        if (use_prefix()) {
            return compare_folded_prefix(get(lhs), get(rhs), _prefix_len) < 0;
        }
        return compare_folded(get(lhs), get(rhs)) < 0;
    }
    bool equal(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const override {
        return compare_folded(get(lhs), get(rhs)) == 0;
    }
};

extern template class EnumStoreComparator<int8_t>;
extern template class EnumStoreComparator<int16_t>;
extern template class EnumStoreComparator<int32_t>;
extern template class EnumStoreComparator<int64_t>;
extern template class EnumStoreComparator<float>;
extern template class EnumStoreComparator<double>;

}

