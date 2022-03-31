// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_matcher.h"
#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

StringMatcher::StringMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : _query_term(static_cast<QueryTermUCS4 *>(query_term.release())),
      _helper(*_query_term, cased)
{
}

StringMatcher::StringMatcher(StringMatcher&&) noexcept = default;

StringMatcher::~StringMatcher() = default;

bool
StringMatcher::isValid() const
{
    return (_query_term && (!_query_term->empty()));
}

}
