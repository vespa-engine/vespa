// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search::attribute {

StringRangeMatcher::StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                                       vespalib::FuzzyMatchingAlgorithm /*fuzzy_matching_algorithm*/)
    : StringRangeMatcher(std::move(query_term), cased) {
}

StringRangeMatcher::StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : _query_term(std::move(query_term)), _query_term_ucs4(nullptr), _range_spec(nullptr), _cased(cased) {
    _query_term_ucs4 = dynamic_cast<QueryTermUCS4*>(_query_term.get());
    if (_query_term) {
        _range_spec = _query_term->get_string_range_spec();
    }
}

StringRangeMatcher::StringRangeMatcher(StringRangeMatcher&&) noexcept = default;

StringRangeMatcher::~StringRangeMatcher() = default;

bool StringRangeMatcher::isValid() const {
    return _query_term && (!_query_term->empty()) && _range_spec;
}

bool StringRangeMatcher::match(const char* src) const {
    if (_cased) {
        return match_internal<false>(src);
    } else {
        return match_internal<true>(src);
    }
}

const QueryTermUCS4* StringRangeMatcher::get_query_term_ptr() const noexcept {
    return _query_term_ucs4;
}

void StringRangeMatcher::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                            EnumHintSearchContext&         enum_hint_sc) {
    if (isValid()) {
        auto comp_left = enum_store.make_folded_comparator(_range_spec->left.c_str());
        auto comp_right = enum_store.make_folded_comparator(_range_spec->right.c_str());
        enum_hint_sc.lookupRange(comp_left, comp_right);
    }
}

template <bool fold>
bool StringRangeMatcher::match_internal(const char* src) const {
    if (isValid()) {
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
