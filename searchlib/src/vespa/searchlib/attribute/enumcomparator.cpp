// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumcomparator.h"

#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search {

template <typename EntryT>
bool EnumStoreComparator<EntryT>::equal_helper(const EntryT& lhs, const EntryT& rhs) noexcept {
    return vespalib::datastore::UniqueStoreComparatorHelper<EntryT>::equal(lhs, rhs);
}

namespace enumstorestringcomparator {

namespace {

const char* to_string(CompareStrategy strategy) noexcept {
    switch (strategy) {
    case CompareStrategy::UNCASED_THEN_CASED:
        return "UNCASED_THEN_CASED";
    case CompareStrategy::UNCASED:
        return "UNCASED";
    case CompareStrategy::CASED:
        return "CASED";
    }
    return "<unknown>";
}

/*
 * Returns the strategy identified by <ToS, ToP, ToL> when Strategy can hold it.
 * When it cannot, the transformation has no faithful result, so it throws
 * instead of substituting a strategy that compares differently. <FromS, FromP,
 * FromL> and transform only name the rejected transformation in the message.
 */
template <CompareStrategy FromS, bool FromP, bool FromL, CompareStrategy ToS, bool ToP, bool ToL>
Strategy transformed(const char* transform) {
    using Target = EnumStoreStringCompareStrategy<ToS, ToP, ToL>;
    if constexpr (is_strategy_alternative_v<Target>) {
        return Target();
    } else {
        throw vespalib::IllegalArgumentException(
            vespalib::make_string("EnumStoreStringComparator::%s is not supported for compare strategy "
                                  "{%s, prefix=%s, less_or_equal=%s}: the resulting strategy "
                                  "{%s, prefix=%s, less_or_equal=%s} is not one of the supported strategies",
                                  transform, to_string(FromS), FromP ? "true" : "false", FromL ? "true" : "false",
                                  to_string(ToS), ToP ? "true" : "false", ToL ? "true" : "false"));
    }
}

} // namespace

Strategy folded(const Strategy& strategy) noexcept {
    return std::visit(
        []<CompareStrategy S, bool P, bool L>(const EnumStoreStringCompareStrategy<S, P, L>&) noexcept -> Strategy {
            // UNCASED_THEN_CASED folds to UNCASED; LessOrEqual-ness is preserved. Prefix is
            // dropped along with the lookup value, matching the bare comparator make_folded()
            // returns. Every result is a supported strategy, so transformed() cannot throw here.
            constexpr auto Folded = (S == CompareStrategy::UNCASED_THEN_CASED) ? CompareStrategy::UNCASED : S;
            return transformed<S, P, L, Folded, false, L>("make_folded()");
        },
        strategy);
}

Strategy with_prefix(const Strategy& strategy) {
    return std::visit(
        []<CompareStrategy S, bool P, bool L>(const EnumStoreStringCompareStrategy<S, P, L>&) -> Strategy {
            // The compare strategy is carried over unchanged: folding UNCASED_THEN_CASED to
            // UNCASED here would silently compare differently from the comparator this was
            // derived from, so it is rejected instead. Same for LessOrEqual, which has no
            // prefix alternative. Neither is reachable today, since make_for_prefix_lookup()
            // is only ever called on an already-folded, non-LessOrEqual comparator.
            return transformed<S, P, L, S, true, L>("make_for_prefix_lookup()");
        },
        strategy);
}

Strategy less_or_equal(const Strategy& strategy) {
    return std::visit(
        []<CompareStrategy S, bool P, bool L>(const EnumStoreStringCompareStrategy<S, P, L>&) -> Strategy {
            // As in with_prefix(): UNCASED_THEN_CASED is not folded to UNCASED behind the
            // caller's back, and prefix has no LessOrEqual alternative. Neither is reachable
            // today, since range-boundary lookups always go through the folded comparator.
            return transformed<S, P, L, S, P, true>("make_for_less_or_equal_lookup()");
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
