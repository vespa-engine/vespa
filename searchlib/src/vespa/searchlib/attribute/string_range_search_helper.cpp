// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_search_helper.h"

#include <vespa/searchlib/query/string_range_spec.h>
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search::attribute {

StringRangeSearchHelper::StringRangeSearchHelper(const StringRangeSpec* range_spec, bool cased)
    : _range_spec(range_spec), _cased(cased) {
}

StringRangeSearchHelper::~StringRangeSearchHelper() = default;

template <detail::FoldableString T>
bool StringRangeSearchHelper::is_match(T src) const {
    if (_cased) {
        return is_match_internal<false, T>(src);
    } else {
        return is_match_internal<true, T>(src);
    }
}

template bool StringRangeSearchHelper::is_match<const char*>(const char* src) const;
template bool StringRangeSearchHelper::is_match<std::string_view>(std::string_view src) const;

template <bool fold, detail::FoldableString T>
bool StringRangeSearchHelper::is_match_internal(T src) const {
    if (is_valid()) {
        return (!_range_spec->left ||
                (_range_spec->left_closed ? FoldedStringCompare::compareFolded<fold, fold, T, T>(
                                                string_as<T>(*_range_spec->left), src) <= 0
                                          : FoldedStringCompare::compareFolded<fold, fold, T, T>(
                                                string_as<T>(*_range_spec->left), src) < 0)) &&
               (!_range_spec->right ||
                (_range_spec->right_closed ? FoldedStringCompare::compareFolded<fold, fold, T, T>(
                                                 src, string_as<T>(*_range_spec->right)) <= 0
                                           : FoldedStringCompare::compareFolded<fold, fold, T, T>(
                                                 src, string_as<T>(*_range_spec->right)) < 0));
    } else {
        return true;
    }
}

template bool StringRangeSearchHelper::is_match_internal<false, const char*>(const char* src) const;
template bool StringRangeSearchHelper::is_match_internal<true, const char*>(const char* src) const;
template bool StringRangeSearchHelper::is_match_internal<false, std::string_view>(std::string_view src) const;
template bool StringRangeSearchHelper::is_match_internal<true, std::string_view>(std::string_view src) const;

} // namespace search::attribute
