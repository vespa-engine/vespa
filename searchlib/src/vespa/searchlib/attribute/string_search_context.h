// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "search_context.h"
#include "string_matcher.h"

namespace search {

template <class EntryT> class EnumStoreT;

}

namespace search::attribute {

class EnumHintSearchContext;

/*
 * StringSearchContext is an abstract base class for search contexts
 * handling a query term on a string attribute vector.
 */
class StringSearchContext : public SearchContext, public StringMatcher
{
protected:
    using MatcherType = StringMatcher;
public:
    StringSearchContext(const AttributeVector& to_be_searched, std::unique_ptr<QueryTermSimple> query_term, bool cased);
    StringSearchContext(const AttributeVector& to_be_searched, StringMatcher&& matcher);
    StringSearchContext(StringSearchContext &&) noexcept;
    ~StringSearchContext() override;
    const QueryTermUCS4* queryTerm() const override;
    bool valid() const override;

    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);
};

}
