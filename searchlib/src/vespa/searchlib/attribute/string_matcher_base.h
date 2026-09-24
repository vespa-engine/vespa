// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search {
class QueryTermSimple;
class QueryTermUCS4;
} // namespace search

namespace search::attribute {

/*
 * Base class for the classes used to determine if an attribute vector string value
 * is a match for the query string value. Owns the query term.
 */
class StringMatcherBase {
private:
    std::unique_ptr<QueryTermUCS4> _query_term;

public:
    explicit StringMatcherBase(std::unique_ptr<QueryTermSimple> query_term);
    StringMatcherBase(StringMatcherBase&&) noexcept;
    ~StringMatcherBase();

protected:
    [[nodiscard]] bool is_valid() const;
    [[nodiscard]] const QueryTermUCS4* get_query_term_ptr() const noexcept { return _query_term.get(); }
    [[nodiscard]] const QueryTermUCS4& get_query_term() const noexcept { return *_query_term; }
};

} // namespace search::attribute
