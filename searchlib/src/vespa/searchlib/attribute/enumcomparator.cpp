// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumcomparator.h"

#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search {

template <typename EntryT>
bool EnumStoreComparator<EntryT>::equal_helper(const EntryT& lhs, const EntryT& rhs) noexcept {
    return vespalib::datastore::UniqueStoreComparatorHelper<EntryT>::equal(lhs, rhs);
}

namespace enumstorestringcomparator {

Strategy folded(const Strategy& strategy) noexcept {
    return std::visit(
        []<CompareStrategy S, bool P, bool L>(const EnumStoreStringCompareStrategy<S, P, L>&) noexcept -> Strategy {
            // Non-prefix, LessOrEqual-ness preserved; UNCASED_THEN_CASED folds to UNCASED.
            if constexpr (S == CompareStrategy::UNCASED_THEN_CASED) {
                return EnumStoreStringCompareStrategy<CompareStrategy::UNCASED, false, L>();
            } else {
                return EnumStoreStringCompareStrategy<S, false, L>();
            }
        },
        strategy);
}

Strategy with_prefix(const Strategy& strategy) noexcept {
    return std::visit(
        []<CompareStrategy S, bool P, bool L>(const EnumStoreStringCompareStrategy<S, P, L>&) noexcept -> Strategy {
            // Prefix, never LessOrEqual (prefix + range-boundary isn't a real combination);
            // UNCASED_THEN_CASED has no prefix variant in the Strategy variant (never reached
            // in practice, since with_prefix() is only called on an already-folded comparator),
            // so fold it first, same as folded() does.
            if constexpr (S == CompareStrategy::UNCASED_THEN_CASED) {
                return UncasedPrefix();
            } else {
                return EnumStoreStringCompareStrategy<S, true, false>();
            }
        },
        strategy);
}

Strategy less_or_equal(const Strategy& strategy) noexcept {
    return std::visit(
        []<CompareStrategy S, bool P, bool L>(const EnumStoreStringCompareStrategy<S, P, L>&) noexcept -> Strategy {
            // Non-prefix, LessOrEqual; UNCASED_THEN_CASED folds to UNCASED first, same as
            // folded() does (range-boundary lookups always go through the folded comparator).
            if constexpr (S == CompareStrategy::UNCASED_THEN_CASED) {
                return UncasedLessOrEqual();
            } else {
                return EnumStoreStringCompareStrategy<S, false, true>();
            }
        },
        strategy);
}

} // namespace enumstorestringcomparator

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType&                data_store,
                                                     enumstorestringcomparator::Strategy strategy) noexcept
    : ParentType(data_store, nullptr), _strategy(strategy), _prefix_len(0) {
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType&                data_store,
                                                     enumstorestringcomparator::Strategy strategy,
                                                     const char*                         lookup_value) noexcept
    : ParentType(data_store, lookup_value), _strategy(strategy), _prefix_len(0) {
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType&                data_store,
                                                     enumstorestringcomparator::Strategy strategy,
                                                     const char* lookup_value, uint32_t prefix_len) noexcept
    : ParentType(data_store, lookup_value), _strategy(strategy), _prefix_len(prefix_len) {
}

bool EnumStoreStringComparator::less(vespalib::datastore::EntryRef lhs,
                                     vespalib::datastore::EntryRef rhs) const noexcept {
    return std::visit(
        [this, lhs, rhs](const auto& strategy) noexcept { return strategy.less(get(lhs), get(rhs), _prefix_len); },
        _strategy);
}

template class EnumStoreComparator<int8_t>;
template class EnumStoreComparator<int16_t>;
template class EnumStoreComparator<int32_t>;
template class EnumStoreComparator<int64_t>;
template class EnumStoreComparator<float>;
template class EnumStoreComparator<double>;

} // namespace search
