// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_matcher.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search::attribute {

StringRangeMatcher::StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                                       vespalib::FuzzyMatchingAlgorithm /*fuzzy_matching_algorithm*/)
    : _query_term(std::move(query_term)), _left(), _right(), _cased(cased) {
    QueryTermSimple::RangeResult<std::string> res = _query_term->getRange<std::string>();
    _left = std::move(res.low);
    _right = std::move(res.high);
}

StringRangeMatcher::StringRangeMatcher(StringRangeMatcher&&) noexcept = default;

StringRangeMatcher::~StringRangeMatcher() = default;

bool StringRangeMatcher::isValid() const {
    return (_query_term && (!_query_term->empty()));
}

bool StringRangeMatcher::match(const char* src) const {
    if (_cased) {
        return FoldedStringCompare::compareFolded<false, false>(_left.c_str(), src) < 0 &&
               FoldedStringCompare::compare(src, _right.c_str()) < 0;
    } else {
        return FoldedStringCompare::compareFolded<true, true>(_left.c_str(), src) < 0 &&
               FoldedStringCompare::compare(src, _right.c_str()) < 0;
    }
}

} // namespace search::attribute
