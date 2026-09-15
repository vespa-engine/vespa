// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"

#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/datastore/entry_comparator.h>
#include <vespa/vespalib/datastore/unique_store_comparator.h>
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>

#include <type_traits>
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
 * two string values. less() is fully resolved at compile time from its three
 * template parameters, so a given instantiation compiles down to exactly one
 * FoldedStringCompare call.
 *
 * LessOrEqual turns less() into a "less than or equal to" test, i.e. it treats
 * equal values as ordered. This is used to build range-boundary comparators for
 * string range search: a lower_bound()-style dictionary walk driven by a
 * less-or-equal comparator for the boundary value skips past values equal to
 * the boundary, which is exactly what's needed for an exclusive (open) range
 * endpoint, as opposed to the inclusive (closed) endpoint an ordinary less()
 * comparator gives.
 */
template <CompareStrategy Strategy, bool Prefix, bool LessOrEqual = false>
struct EnumStoreStringCompareStrategy {
    bool less(const char* lhs, const char* rhs, uint32_t prefix_len) const noexcept {
        int cmp;
        if constexpr (Strategy == CompareStrategy::CASED) {
            if constexpr (Prefix) {
                cmp = FoldedStringCompare::compareFoldedPrefix<false, false>(lhs, rhs, prefix_len);
            } else {
                cmp = FoldedStringCompare::compareFolded<false, false>(lhs, rhs);
            }
        } else if constexpr (Strategy == CompareStrategy::UNCASED) {
            if constexpr (Prefix) {
                cmp = FoldedStringCompare::compareFoldedPrefix<true, true>(lhs, rhs, prefix_len);
            } else {
                cmp = FoldedStringCompare::compareFolded<true, true>(lhs, rhs);
            }
        } else { // UNCASED_THEN_CASED
            if constexpr (Prefix) {
                cmp = FoldedStringCompare::comparePrefix(lhs, rhs, prefix_len);
            } else {
                cmp = FoldedStringCompare::compare(lhs, rhs);
            }
        }
        if constexpr (LessOrEqual) {
            return cmp <= 0;
        } else {
            return cmp < 0;
        }
    }
};

using UncasedThenCased = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED_THEN_CASED, false>;
using Uncased = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, false>;
using UncasedPrefix = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, true>;
using Cased = EnumStoreStringCompareStrategy<CompareStrategy::CASED, false>;
using CasedPrefix = EnumStoreStringCompareStrategy<CompareStrategy::CASED, true>;
using UncasedLessOrEqual = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, false, true>;
using CasedLessOrEqual = EnumStoreStringCompareStrategy<CompareStrategy::CASED, false, true>;

// UncasedThenCased+prefix, and the prefix+LessOrEqual combinations, are never
// constructed by any call site today and are omitted from the variant (the
// template stays total/uniform for them regardless). The transformations below
// throw rather than substitute a different strategy when asked for one of them.
using Strategy =
    std::variant<UncasedThenCased, Uncased, UncasedPrefix, Cased, CasedPrefix, UncasedLessOrEqual, CasedLessOrEqual>;

template <typename T, typename Variant> struct is_alternative_of;

template <typename T, typename... Ts>
struct is_alternative_of<T, std::variant<Ts...>> : std::bool_constant<(std::is_same_v<T, Ts> || ...)> {};

/*
 * True when T is one of the strategies Strategy can hold. The transformations
 * below use it to decide whether the faithful result of the transformation is
 * representable; when it is not, they throw instead of silently returning a
 * strategy that compares differently from the one that was asked for.
 */
template <typename T> inline constexpr bool is_strategy_alternative_v = is_alternative_of<T, Strategy>::value;

// -> non-prefix counterpart used by make_folded(). Dropping the prefix matches the
// bare comparator make_folded() has always returned, so this never throws.
Strategy folded(const Strategy& strategy) noexcept;
// -> prefix counterpart used by make_for_prefix_lookup(). Throws for UNCASED_THEN_CASED
// (which has no prefix alternative) and for a LessOrEqual strategy.
Strategy with_prefix(const Strategy& strategy);
// -> LessOrEqual counterpart used by make_for_less_or_equal_lookup(). Throws for
// UNCASED_THEN_CASED (which has no LessOrEqual alternative) and for a prefix strategy.
Strategy less_or_equal(const Strategy& strategy);

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
    EnumStoreStringComparator make_for_prefix_lookup(const char* lookup_value) const {
        return {_store, enumstorestringcomparator::with_prefix(_strategy), lookup_value,
                static_cast<uint32_t>(FoldedStringCompare::size(lookup_value))};
    }
    /**
     * Creates a comparator that treats lookup_value as ordered before an equal
     * dictionary value (i.e. "less than or equal to" instead of "less than").
     * Used to build an exclusive (open) range-search boundary: driving a
     * lower_bound()-style dictionary walk with this comparator skips past a
     * dictionary entry equal to lookup_value, unlike make_for_lookup()'s
     * ordinary less-than comparator, which includes it.
     */
    EnumStoreStringComparator make_for_less_or_equal_lookup(const char* lookup_value) const {
        return {_store, enumstorestringcomparator::less_or_equal(_strategy), lookup_value};
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
