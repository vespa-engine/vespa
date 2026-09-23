// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_string_comparator.h"

#include <vespa/searchlib/util/foldedstringcompare.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store_string_comparator");

namespace search {

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store,
                                                     CompareStrategy      compare_strategy) noexcept
    : ParentType(data_store, nullptr), _compare_strategy(compare_strategy), _prefix(false), _prefix_len(0) {
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store,
                                                     CompareStrategy      compare_strategy,
                                                     const char*          lookup_value) noexcept
    : ParentType(data_store, lookup_value), _compare_strategy(compare_strategy), _prefix(false), _prefix_len(0) {
}

EnumStoreStringComparator::EnumStoreStringComparator(const DataStoreType& data_store,
                                                     CompareStrategy compare_strategy, const char* lookup_value,
                                                     bool prefix) noexcept
    : ParentType(data_store, lookup_value), _compare_strategy(compare_strategy), _prefix(prefix), _prefix_len(0) {
    if (use_prefix()) {
        _prefix_len = FoldedStringCompare::size(lookup_value);
    }
}

bool EnumStoreStringComparator::less(vespalib::datastore::EntryRef lhs,
                                     vespalib::datastore::EntryRef rhs) const noexcept {
    if (use_prefix()) [[unlikely]] {
        switch (_compare_strategy) {
        case CompareStrategy::CASED:
            return (FoldedStringCompare::compareFoldedPrefix<false, false>(get(lhs), get(rhs), _prefix_len) < 0);
        case CompareStrategy::UNCASED_THEN_CASED:
            LOG_ABORT("Cannot do prefix lookup in UNCASED_THEN_CASED mode");
        case CompareStrategy::UNCASED:
        default:
            return (FoldedStringCompare::compareFoldedPrefix<true, true>(get(lhs), get(rhs), _prefix_len) < 0);
        }
    } else {
        switch (_compare_strategy) {
        case CompareStrategy::UNCASED:
            return (FoldedStringCompare::compareFolded<true, true>(get(lhs), get(rhs)) < 0);
        case CompareStrategy::CASED:
            return (FoldedStringCompare::compareFolded<false, false>(get(lhs), get(rhs)) < 0);
        case CompareStrategy::UNCASED_THEN_CASED:
        default:
            return (FoldedStringCompare::compare(get(lhs), get(rhs)) < 0);
        }
    }
}

} // namespace search
