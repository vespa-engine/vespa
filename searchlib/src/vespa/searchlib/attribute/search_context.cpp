// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search_context.h"
#include "attributevector.h"
#include "attributeiterators.hpp"
#include "ipostinglistsearchcontext.h"
#include <vespa/searchlib/queryeval/emptysearch.h>

using search::queryeval::SearchIterator;

namespace search::attribute {

SearchContext::SearchContext(const AttributeVector &attr)
    : _attr(attr),
      _plsc(nullptr)
{
}

SearchContext::~SearchContext() = default;

unsigned int
SearchContext::approximateHits() const
{
    if (_plsc != nullptr) {
        return _plsc->approximateHits();
    }
    return std::max(uint64_t(_attr.getNumDocs()), _attr.getStatus().getNumValues());
}

std::unique_ptr<SearchIterator>
SearchContext::createIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (_plsc != nullptr) {
        std::unique_ptr<SearchIterator> res = _plsc->createPostingIterator(matchData, strict);
        if (res) {
            return res;
        }
    }
    return createFilterIterator(matchData, strict);
}


std::unique_ptr<SearchIterator>
SearchContext::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return (strict ?
                std::make_unique<FilterAttributeIteratorStrict<SearchContext>>(*this, matchData) :
                std::make_unique<FilterAttributeIteratorT<SearchContext>>(*this, matchData));
    }
    return (strict ?
            std::make_unique<AttributeIteratorStrict<SearchContext>>(*this, matchData) :
            std::make_unique<AttributeIteratorT<SearchContext>>(*this, matchData));
}


void
SearchContext::fetchPostings(const queryeval::ExecuteInfo& execInfo)
{
    if (_plsc != nullptr) {
        _plsc->fetchPostings(execInfo);
    }
}

const vespalib::string&
SearchContext::attributeName() const
{
    return _attr.getName();
}

bool
SearchContext::getIsFilter() const
{
    return _attr.getIsFilter();
}


}
