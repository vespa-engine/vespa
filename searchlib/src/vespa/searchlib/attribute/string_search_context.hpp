// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumhintsearchcontext.h"
#include "enumstore.h"
#include "string_search_context.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/util/regexp.h>

namespace search::attribute {

template <typename Matcher>
StringSearchContextT<Matcher>::StringSearchContextT(const AttributeVector&           to_be_searched,
                                                    std::unique_ptr<QueryTermSimple> query_term, bool cased,
                                                    vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm)
    : SearchContext(to_be_searched), Matcher(std::move(query_term), cased, fuzzy_matching_algorithm) {
}

template <typename Matcher>
StringSearchContextT<Matcher>::StringSearchContextT(const AttributeVector& to_be_searched, Matcher&& matcher)
    : SearchContext(to_be_searched), Matcher(std::move(matcher)) {
}

template <typename Matcher>
StringSearchContextT<Matcher>::StringSearchContextT(StringSearchContextT&&) noexcept = default;

template <typename Matcher>
StringSearchContextT<Matcher>::~StringSearchContextT() = default;

template <typename Matcher>
const QueryTermUCS4* StringSearchContextT<Matcher>::queryTerm() const {
    return this->get_query_term_ptr();
}

template <typename Matcher>
bool StringSearchContextT<Matcher>::valid() const {
    return Matcher::isValid();
}

template <typename Matcher>
void StringSearchContextT<Matcher>::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                                       EnumHintSearchContext&         enum_hint_sc) {
    _plsc = &enum_hint_sc;
    if (valid()) {
        if (this->isPrefix()) {
            auto comp = enum_store.make_folded_comparator_prefix(queryTerm()->getTerm());
            enum_hint_sc.lookupRange(comp, comp);
        } else if (this->isRegex()) {
            std::string prefix(vespalib::RegexpUtil::get_prefix(queryTerm()->getTerm()));
            auto        comp = enum_store.make_folded_comparator_prefix(prefix.c_str());
            enum_hint_sc.lookupRange(comp, comp);
        } else if (this->isFuzzy()) {
            std::string prefix(this->getFuzzyMatcher().getPrefix());
            auto        comp = enum_store.make_folded_comparator_prefix(prefix.c_str());
            enum_hint_sc.lookupRange(comp, comp);
        } else {
            auto comp = enum_store.make_folded_comparator(queryTerm()->getTerm());
            enum_hint_sc.lookupTerm(comp);
        }
    }
}

} // namespace search::attribute
