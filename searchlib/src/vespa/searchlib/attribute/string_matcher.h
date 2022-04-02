// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_search_helper.h"

namespace search { class QueryTermSimple; }

namespace search::attribute {

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string value.
 */
class StringMatcher
{
private:
    std::unique_ptr<QueryTermUCS4> _query_term;
    attribute::StringSearchHelper  _helper;
public:
    StringMatcher(std::unique_ptr<QueryTermSimple> qTerm, bool cased);
    StringMatcher(StringMatcher&&) noexcept;
    ~StringMatcher();
protected:
    bool isValid() const;
    bool match(const char *src) const { return _helper.isMatch(src); }
    bool isPrefix() const { return _helper.isPrefix(); }
    bool isRegex() const { return _helper.isRegex(); }
    bool isCased() const { return _helper.isCased(); }
    bool isFuzzy() const { return _helper.isFuzzy(); }
    const vespalib::Regex& getRegex() const { return _helper.getRegex(); }
    const vespalib::FuzzyMatcher& getFuzzyMatcher() const { return _helper.getFuzzyMatcher(); }
    const QueryTermUCS4* get_query_term_ptr() const noexcept { return _query_term.get(); }
};

}
