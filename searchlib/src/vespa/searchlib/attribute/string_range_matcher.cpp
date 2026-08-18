// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_matcher.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

StringRangeMatcher::StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool /*cased*/,
                                       vespalib::FuzzyMatchingAlgorithm /*fuzzy_matching_algorithm*/)
    : _query_term(std::move(query_term)), _low(), _high() {
    QueryTermSimple::RangeResult<std::string> res = _query_term->getRange<std::string>();
    _low = std::move(res.low);
    _high = std::move(res.high);
}

StringRangeMatcher::StringRangeMatcher(StringRangeMatcher&&) noexcept = default;

StringRangeMatcher::~StringRangeMatcher() = default;

bool StringRangeMatcher::isValid() const {
    return (_query_term && (!_query_term->empty()));
}

bool StringRangeMatcher::match(const char* src) const {
    return src[0] >= 'A' && src[0] <= 'H';
}

} // namespace search::attribute
