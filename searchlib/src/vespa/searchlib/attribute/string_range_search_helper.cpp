// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_search_helper.h"

#include <vespa/searchlib/query/string_range_spec.h>
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search::attribute {

StringRangeSearchHelper::StringRangeSearchHelper(const StringRangeSpec* range_spec, bool cased)
    : _range_spec(range_spec), _cased(cased) {
}

StringRangeSearchHelper::~StringRangeSearchHelper() = default;

bool StringRangeSearchHelper::is_match(const char* src) const {
    if (_cased) {
        return is_match_internal<false>(src);
    } else {
        return is_match_internal<true>(src);
    }
}

template <bool fold>
bool StringRangeSearchHelper::is_match_internal(const char* src) const {
    if (is_valid()) {
        return (_range_spec->left_unbounded ||
                (_range_spec->left_closed
                     ? FoldedStringCompare::compareFolded<fold, fold>(_range_spec->left.c_str(), src) <= 0
                     : FoldedStringCompare::compareFolded<fold, fold>(_range_spec->left.c_str(), src) < 0)) &&
               (_range_spec->right_unbounded ||
                (_range_spec->right_closed
                     ? FoldedStringCompare::compareFolded<fold, fold>(src, _range_spec->right.c_str()) <= 0
                     : FoldedStringCompare::compareFolded<fold, fold>(src, _range_spec->right.c_str()) < 0));
    } else {
        return true;
    }
}

} // namespace search::attribute
