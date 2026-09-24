// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_matcher_base.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

StringMatcherBase::StringMatcherBase(std::unique_ptr<QueryTermSimple> query_term)
    : _query_term(static_cast<QueryTermUCS4*>(query_term.release())) {
}

StringMatcherBase::StringMatcherBase(StringMatcherBase&&) noexcept = default;

StringMatcherBase::~StringMatcherBase() = default;

bool StringMatcherBase::is_valid() const {
    return (_query_term && (!_query_term->empty()));
}

} // namespace search::attribute
