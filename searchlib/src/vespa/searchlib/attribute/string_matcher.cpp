// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/util/regexp.h>

namespace search::attribute {

StringMatcher::StringMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                             vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm)
    : _query_term(static_cast<QueryTermUCS4*>(query_term.release())),
      _helper(*_query_term, cased, fuzzy_matching_algorithm) {
}

StringMatcher::StringMatcher(StringMatcher&&) noexcept = default;

StringMatcher::~StringMatcher() = default;

bool StringMatcher::isValid() const {
    return (_query_term && (!_query_term->empty()));
}

void StringMatcher::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                       EnumHintSearchContext&         enum_hint_sc) {
    if (isValid()) {
        if (isPrefix()) {
            auto comp = enum_store.make_folded_comparator_prefix(get_query_term_ptr()->getTerm());
            enum_hint_sc.lookupRange(comp, comp);
        } else if (isRegex()) {
            std::string prefix(vespalib::RegexpUtil::get_prefix(get_query_term_ptr()->getTerm()));
            auto        comp = enum_store.make_folded_comparator_prefix(prefix.c_str());
            enum_hint_sc.lookupRange(comp, comp);
        } else if (isFuzzy()) {
            std::string prefix(getFuzzyMatcher().getPrefix());
            auto        comp = enum_store.make_folded_comparator_prefix(prefix.c_str());
            enum_hint_sc.lookupRange(comp, comp);
        } else {
            auto comp = enum_store.make_folded_comparator(get_query_term_ptr()->getTerm());
            enum_hint_sc.lookupTerm(comp);
        }
    }
}

} // namespace search::attribute
