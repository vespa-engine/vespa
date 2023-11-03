// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumcomparator.h"
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search {

template <typename EntryT>
bool
EnumStoreComparator<EntryT>::equal_helper(const EntryT& lhs, const EntryT& rhs)
{
    return vespalib::datastore::UniqueStoreComparatorHelper<EntryT>::equal(lhs, rhs);
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy)
    : ParentType(data_store, nullptr),
      _compare_strategy(compare_strategy),
      _prefix(false),
      _prefix_len(0)
{
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy, const char* lookup_value)
    : ParentType(data_store, lookup_value),
      _compare_strategy(compare_strategy),
      _prefix(false),
      _prefix_len(0)
{
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store, CompareStrategy compare_strategy, const char* lookup_value, bool prefix)
    : ParentType(data_store, lookup_value),
      _compare_strategy(compare_strategy),
      _prefix(prefix),
      _prefix_len(0)
{
    if (use_prefix()) {
        _prefix_len = FoldedStringCompare::size(lookup_value);
    }
}

bool
EnumStoreStringComparator::less(vespalib::datastore::EntryRef lhs, vespalib::datastore::EntryRef rhs) const {
    switch (_compare_strategy) {
    case CompareStrategy::UNCASED:
        return (use_prefix()
           ? (FoldedStringCompare::compareFoldedPrefix<true, true>(get(lhs), get(rhs), _prefix_len) < 0)
           : (FoldedStringCompare::compareFolded<true, true>(get(lhs), get(rhs)) < 0));
    case CompareStrategy::CASED:
        return (use_prefix()
                ? (FoldedStringCompare::compareFoldedPrefix<false, false>(get(lhs), get(rhs), _prefix_len) < 0)
                : (FoldedStringCompare::compareFolded<false, false>(get(lhs), get(rhs)) < 0));
    case CompareStrategy::UNCASED_THEN_CASED:
    default:
        return (use_prefix()
           ? (FoldedStringCompare::comparePrefix(get(lhs), get(rhs), _prefix_len) < 0)
           : (FoldedStringCompare::compare(get(lhs), get(rhs)) < 0));
    }
}

template class EnumStoreComparator<int8_t>;
template class EnumStoreComparator<int16_t>;
template class EnumStoreComparator<int32_t>;
template class EnumStoreComparator<int64_t>;
template class EnumStoreComparator<float>;
template class EnumStoreComparator<double>;

}

