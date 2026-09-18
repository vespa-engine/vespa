// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"

#include <vespa/vespalib/datastore/entry_comparator.h>
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>

namespace search {

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
    enum class CompareStrategy : uint8_t { UNCASED_THEN_CASED, UNCASED, CASED };

    EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy) noexcept;

public:
    explicit EnumStoreStringComparator(const DataStoreType& data_store) noexcept
        : EnumStoreStringComparator(data_store, CompareStrategy::UNCASED_THEN_CASED) {}
    EnumStoreStringComparator(const DataStoreType& data_store, bool cased) noexcept
        : EnumStoreStringComparator(data_store,
                                    cased ? CompareStrategy::CASED : CompareStrategy::UNCASED_THEN_CASED) {}

private:
    /**
     * Creates a comparator using the given low-level data store and that uses the
     * given value during compare if the enum index is invalid.
     */
    EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy,
                              const char* lookup_value) noexcept;
    EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy,
                              const char* lookup_value, bool prefix) noexcept;

public:
    bool less(vespalib::datastore::EntryRef lhs, vespalib::datastore::EntryRef rhs) const noexcept override;
    EnumStoreStringComparator make_folded() const noexcept {
        return {_store,
                _compare_strategy == CompareStrategy::UNCASED_THEN_CASED ? CompareStrategy::UNCASED
                                                                         : _compare_strategy,
                nullptr, false};
    }
    EnumStoreStringComparator make_for_lookup(const char* lookup_value) const noexcept {
        return {_store, _compare_strategy, lookup_value, false};
    }
    EnumStoreStringComparator make_for_prefix_lookup(const char* lookup_value) const noexcept {
        return {_store, _compare_strategy, lookup_value, true};
    }

private:
    bool use_prefix() const noexcept { return _prefix; }
    const CompareStrategy _compare_strategy;
    const bool            _prefix;
    uint32_t              _prefix_len;
};

class UncasedComparator : public EnumStoreStringComparator {
public:
    explicit UncasedComparator(const DataStoreType& data_store) noexcept
        : EnumStoreStringComparator(data_store, CompareStrategy::UNCASED) {}
};

class UncasedThenCasedComparator : public EnumStoreStringComparator {
public:
    explicit UncasedThenCasedComparator(const DataStoreType& data_store) noexcept
        : EnumStoreStringComparator(data_store, CompareStrategy::UNCASED_THEN_CASED) {}
};

class CasedComparator : public EnumStoreStringComparator {
public:
    explicit CasedComparator(const DataStoreType& data_store) noexcept
        : EnumStoreStringComparator(data_store, CompareStrategy::CASED) {}
};

} // namespace search
