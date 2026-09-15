// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"

#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/datastore/entry_comparator.h>
#include <vespa/vespalib/datastore/unique_store_comparator.h>
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>

#include <variant>

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

    EnumStoreComparator<EntryT> make_folded() const noexcept { return *this; }
    EnumStoreComparator<EntryT> make_for_lookup(const EntryT& lookup_value) const noexcept {
        return {this->_store, lookup_value};
    }
};

namespace enumstorestringcomparator {

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

/*
 * Stateless tag type used by EnumStoreStringComparator to select how to compare
 * two string values. less() is fully resolved at compile time from its two
 * template parameters, so a given instantiation compiles down to exactly one
 * FoldedStringCompare call.
 */
template <CompareStrategy Strategy, bool Prefix>
struct EnumStoreStringCompareStrategy {
    bool less(const char* lhs, const char* rhs, uint32_t prefix_len) const noexcept {
        if constexpr (Strategy == CompareStrategy::CASED) {
            if constexpr (Prefix) {
                return FoldedStringCompare::compareFoldedPrefix<false, false>(lhs, rhs, prefix_len) < 0;
            } else {
                return FoldedStringCompare::compareFolded<false, false>(lhs, rhs) < 0;
            }
        } else if constexpr (Strategy == CompareStrategy::UNCASED) {
            if constexpr (Prefix) {
                return FoldedStringCompare::compareFoldedPrefix<true, true>(lhs, rhs, prefix_len) < 0;
            } else {
                return FoldedStringCompare::compareFolded<true, true>(lhs, rhs) < 0;
            }
        } else { // UNCASED_THEN_CASED
            if constexpr (Prefix) {
                return FoldedStringCompare::comparePrefix(lhs, rhs, prefix_len) < 0;
            } else {
                return FoldedStringCompare::compare(lhs, rhs) < 0;
            }
        }
    }
};

using UncasedThenCased = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED_THEN_CASED, false>;
using Uncased = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, false>;
using UncasedPrefix = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, true>;
using Cased = EnumStoreStringCompareStrategy<CompareStrategy::CASED, false>;
using CasedPrefix = EnumStoreStringCompareStrategy<CompareStrategy::CASED, true>;

// UncasedThenCased+prefix is never constructed by any call site today; omitted from the
// variant (comparePrefix() above is kept only so the template stays total/uniform).
using Strategy = std::variant<UncasedThenCased, Uncased, UncasedPrefix, Cased, CasedPrefix>;

Strategy folded(const Strategy& strategy) noexcept;      // -> non-prefix counterpart used by make_folded()
Strategy with_prefix(const Strategy& strategy) noexcept; // -> prefix counterpart used by make_for_prefix_lookup()

} // namespace enumstorestringcomparator

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
    explicit EnumStoreStringComparator(const DataStoreType& data_store) noexcept
        : EnumStoreStringComparator(data_store, enumstorestringcomparator::UncasedThenCased()) {}
    EnumStoreStringComparator(const DataStoreType& data_store, bool cased) noexcept
        : EnumStoreStringComparator(
              data_store, cased
                              ? enumstorestringcomparator::Strategy(enumstorestringcomparator::Cased())
                              : enumstorestringcomparator::Strategy(enumstorestringcomparator::UncasedThenCased())) {}

private:
    EnumStoreStringComparator(const DataStoreType& data_store, enumstorestringcomparator::Strategy strategy) noexcept;

    /**
     * Creates a comparator using the given low-level data store and that uses the
     * given value during compare if the enum index is invalid.
     */
    EnumStoreStringComparator(const DataStoreType& data_store, enumstorestringcomparator::Strategy strategy,
                              const char* lookup_value) noexcept;
    EnumStoreStringComparator(const DataStoreType& data_store, enumstorestringcomparator::Strategy strategy,
                              const char* lookup_value, uint32_t prefix_len) noexcept;

public:
    bool less(vespalib::datastore::EntryRef lhs, vespalib::datastore::EntryRef rhs) const noexcept override;
    EnumStoreStringComparator make_folded() const noexcept {
        return {_store, enumstorestringcomparator::folded(_strategy)};
    }
    EnumStoreStringComparator make_for_lookup(const char* lookup_value) const noexcept {
        return {_store, _strategy, lookup_value};
    }
    EnumStoreStringComparator make_for_prefix_lookup(const char* lookup_value) const noexcept {
        return {_store, enumstorestringcomparator::with_prefix(_strategy), lookup_value,
                static_cast<uint32_t>(FoldedStringCompare::size(lookup_value))};
    }

private:
    enumstorestringcomparator::Strategy _strategy;
    uint32_t                            _prefix_len;
};

extern template class EnumStoreComparator<int8_t>;
extern template class EnumStoreComparator<int16_t>;
extern template class EnumStoreComparator<int32_t>;
extern template class EnumStoreComparator<int64_t>;
extern template class EnumStoreComparator<float>;
extern template class EnumStoreComparator<double>;

} // namespace search
