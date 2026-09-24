// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"

#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/datastore/entry_comparator.h>
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>

#include <cstdlib>
#include <variant>

namespace search {

namespace string_comparator {

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
template <CompareStrategy Strategy, bool Prefix = false, bool LessOrEqual = false>
struct EnumStoreStringCompareStrategy {
    constexpr CompareStrategy strategy() const { return Strategy; }
    constexpr bool is_prefix() const { return Prefix; }
    constexpr bool is_less_or_equal() const { return LessOrEqual; }

    bool less(const char* lhs, const char* rhs, uint32_t prefix_len) const noexcept {
        int cmp;
        if constexpr (Prefix) {
            if constexpr (Strategy == CompareStrategy::CASED) {
                cmp = FoldedStringCompare::compareFoldedPrefix<false, false>(lhs, rhs, prefix_len);
            } else if constexpr (Strategy == CompareStrategy::UNCASED) {
                cmp = FoldedStringCompare::compareFoldedPrefix<true, true>(lhs, rhs, prefix_len);
            } else {
                static_assert(false);
            }
        } else {
            if constexpr (Strategy == CompareStrategy::CASED) {
                cmp = FoldedStringCompare::compareFolded<false, false>(lhs, rhs);
            } else if constexpr (Strategy == CompareStrategy::UNCASED) {
                cmp = FoldedStringCompare::compareFolded<true, true>(lhs, rhs);
            } else { // UNCASED_THEN_CASED
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

using Cased = EnumStoreStringCompareStrategy<CompareStrategy::CASED, false>;
using Uncased = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, false>;
using UncasedThenCased = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED_THEN_CASED, false>;

using CasedPrefix = EnumStoreStringCompareStrategy<CompareStrategy::CASED, true>;
using UncasedPrefix = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, true>;

using CasedLessOrEqual = EnumStoreStringCompareStrategy<CompareStrategy::CASED, false, true>;
using UncasedLessOrEqual = EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, false, true>;

using OuterStrategy =
    std::variant<Cased, Uncased, UncasedThenCased, CasedPrefix, UncasedPrefix, CasedLessOrEqual, UncasedLessOrEqual>;

} // namespace string_comparator

/**
 * Less-than comparator used for comparing strings stored in an enum store.
 *
 * The input string values are first folded, then compared.
 * If they are equal, then it falls back to comparing without folding.
 */
class EnumStoreStringComparator : public vespalib::datastore::UniqueStoreStringComparator<IEnumStore::InternalIndex> {
private:
    using ParentType = vespalib::datastore::UniqueStoreStringComparator<IEnumStore::InternalIndex>;
    using DataStoreType = ParentType::DataStoreType;
    using ParentType::get;
    using CompareStrategy = string_comparator::CompareStrategy;
    using Strategy = string_comparator::OuterStrategy;

    const Strategy _strategy;
    uint32_t       _prefix_len;

    EnumStoreStringComparator(const DataStoreType& data_store, Strategy strategy, const char* lookup_value,
                              bool is_prefix) noexcept
        : ParentType(data_store, lookup_value), _strategy(strategy), _prefix_len(0) {
        if (is_prefix) {
            _prefix_len = FoldedStringCompare::size(lookup_value);
        }
    }

public:
    EnumStoreStringComparator(const DataStoreType& data_store) noexcept
        : EnumStoreStringComparator(data_store, string_comparator::UncasedThenCased(), nullptr, false) {}

    static EnumStoreStringComparator cased(const DataStoreType& data_store) noexcept {
        return EnumStoreStringComparator(data_store, string_comparator::Cased(), nullptr, false);
    }

    bool less(vespalib::datastore::EntryRef lhs, vespalib::datastore::EntryRef rhs) const noexcept override;

    EnumStoreStringComparator make_folded() const noexcept {
        if (std::holds_alternative<string_comparator::UncasedThenCased>(_strategy)) {
            return {_store, string_comparator::Uncased(), nullptr, false};
        }
        abort();
    }

    EnumStoreStringComparator make_for_lookup(const char* lookup_value) const noexcept {
        return {_store, _strategy, lookup_value, false};
    }

    EnumStoreStringComparator make_for_prefix_lookup(const char* lookup_value) const noexcept {
        if (std::holds_alternative<string_comparator::Uncased>(_strategy)) {
            return {_store, string_comparator::UncasedPrefix(), lookup_value, true};
        }
        if (std::holds_alternative<string_comparator::Cased>(_strategy)) {
            return {_store, string_comparator::CasedPrefix(), lookup_value, true};
        }
        abort();
    }

    /**
     * Creates a comparator that treats lookup_value as ordered before an equal
     * dictionary value (i.e. "less than or equal to" instead of "less than").
     * Used to build an exclusive (open) range-search boundary: driving a
     * lower_bound()-style dictionary walk with this comparator skips past a
     * dictionary entry equal to lookup_value, unlike make_for_lookup()'s
     * ordinary less-than comparator, which includes it.
     */
    EnumStoreStringComparator make_for_less_or_equal_lookup(const char* lookup_value) const noexcept {
        if (std::holds_alternative<string_comparator::Uncased>(_strategy)) {
            return {_store, string_comparator::UncasedLessOrEqual(), lookup_value, false};
        }
        if (std::holds_alternative<string_comparator::Cased>(_strategy)) {
            return {_store, string_comparator::CasedLessOrEqual(), lookup_value, false};
        }
        abort();
    }
};

} // namespace search
