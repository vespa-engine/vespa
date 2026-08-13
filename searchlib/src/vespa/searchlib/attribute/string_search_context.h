// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "search_context.h"
#include "string_matcher.h"

#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search {

template <class EntryT> class EnumStoreT;

}

namespace search::attribute {

class EnumHintSearchContext;

/*
 * StringSearchContext is an abstract base class for search contexts
 * handling a query term on a string attribute vector.
 */
template <typename Matcher>
class StringSearchContextT : public SearchContext, public Matcher {
protected:
    using MatcherType = Matcher;

public:
    StringSearchContextT(const AttributeVector& to_be_searched, std::unique_ptr<QueryTermSimple> query_term,
                         bool cased, vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm);
    StringSearchContextT(const AttributeVector& to_be_searched, Matcher&& matcher);
    StringSearchContextT(StringSearchContextT&&) noexcept;
    ~StringSearchContextT() override;
    const QueryTermUCS4* queryTerm() const override;
    bool valid() const override;

    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);
};

using StringSearchContext = StringSearchContextT<StringMatcher>;

} // namespace search::attribute
