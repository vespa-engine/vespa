// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    explicit EnumStoreComparator(const DataStoreType& data_store)
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
        return {this->_store, lookup_value};
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

    /*
     * CompareStrategy determines how to compare string values:
     *
     * UNCASED_THEN_CASED compares the values ignoring case (i.e.
     * performs case folding during the first compare). If the values
     * are equal then they are compared again using case.
     *
     * UNCASED compares the values ignoring case.
     *
     * CASED compares the value, using case.
     *
     * Only UNCASED_THEN_CASED or CASED can be used for sorting.
     * UNCASED can be used for lookup during search when sort order is
     * UNCASED_THEN_CASED.
     *
     * UNCASED_THEN_CASED sort order: ["BAR", "bar", "FOO", "foo"]
     * CASED sort order:              ["BAR", "FOO", "bar", "foo"]
     */
    enum class CompareStrategy : uint8_t {
        UNCASED_THEN_CASED,
        UNCASED,
        CASED
    };

public:
    explicit EnumStoreStringComparator(const DataStoreType& data_store)
        : EnumStoreStringComparator(data_store, CompareStrategy::UNCASED_THEN_CASED)
    {}
    EnumStoreStringComparator(const DataStoreType& data_store, bool cased)
        : EnumStoreStringComparator(data_store, cased ? CompareStrategy::CASED : CompareStrategy::UNCASED_THEN_CASED)
    {}

private:
    EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy);

    /**
     * Creates a comparator using the given low-level data store and that uses the
     * given value during compare if the enum index is invalid.
     */
    EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy, const char* lookup_value);
    EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy, const char* lookup_value, bool prefix);

public:
    bool less(vespalib::datastore::EntryRef lhs, vespalib::datastore::EntryRef rhs) const override;
    EnumStoreStringComparator make_folded() const {
        return {_store, _compare_strategy == CompareStrategy::UNCASED_THEN_CASED ? CompareStrategy::UNCASED : _compare_strategy};
    }
    EnumStoreStringComparator make_for_lookup(const char* lookup_value) const {
        return {_store, _compare_strategy, lookup_value};
    }
    EnumStoreStringComparator make_for_prefix_lookup(const char* lookup_value) const {
        return {_store, _compare_strategy, lookup_value, true};
    }
private:
    inline bool use_prefix() const noexcept { return _prefix; }
    const CompareStrategy _compare_strategy;
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
