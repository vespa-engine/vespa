// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_search_context.h"
#include "enumhintsearchcontext.h"
#include "enumstore.h"
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>

namespace search::attribute {

StringSearchContext::StringSearchContext(const AttributeVector& to_be_searched, std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : SearchContext(to_be_searched),
      StringMatcher(std::move(query_term), cased)
{
}

StringSearchContext::StringSearchContext(const AttributeVector& to_be_searched, StringMatcher &&matcher)
    : SearchContext(to_be_searched),
      StringMatcher(std::move(matcher))
{
}

StringSearchContext::StringSearchContext(StringSearchContext &&) noexcept = default;
StringSearchContext::~StringSearchContext() = default;

const QueryTermUCS4*
StringSearchContext::queryTerm() const
{
    return get_query_term_ptr();
}

bool
StringSearchContext::valid() const
{
    return StringMatcher::isValid();
}

void
StringSearchContext::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc)
{
    _plsc = &enum_hint_sc;
    if (valid()) {
        if (isPrefix()) {
            auto comp = enum_store.make_folded_comparator_prefix(queryTerm()->getTerm());
            enum_hint_sc.lookupRange(comp, comp);
        } else if (isRegex()) {
            vespalib::string prefix(vespalib::RegexpUtil::get_prefix(queryTerm()->getTerm()));
            auto comp = enum_store.make_folded_comparator_prefix(prefix.c_str());
            enum_hint_sc.lookupRange(comp, comp);
        } else if (isFuzzy()) {
            vespalib::string prefix(getFuzzyMatcher().getPrefix());
            auto comp = enum_store.make_folded_comparator_prefix(prefix.c_str());
            enum_hint_sc.lookupRange(comp, comp);
        } else {
            auto comp = enum_store.make_folded_comparator(queryTerm()->getTerm());
            enum_hint_sc.lookupTerm(comp);
        }
    }
}

}
