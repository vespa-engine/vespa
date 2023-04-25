// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_search_context.h"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

EmptySearchContext::EmptySearchContext(const AttributeVector& attr) noexcept
    : SearchContext(attr)
{
}

EmptySearchContext::~EmptySearchContext() = default;

int32_t
EmptySearchContext::onFind(DocId, int32_t, int32_t&) const
{
    return -1;
}

int32_t
EmptySearchContext::onFind(DocId, int32_t) const
{
    return -1;
}

unsigned int
EmptySearchContext::approximateHits() const
{
    return 0u;
}

uint32_t
EmptySearchContext::get_committed_docid_limit() const noexcept
{
    return 0u;
}

std::unique_ptr<queryeval::SearchIterator>
EmptySearchContext::createIterator(fef::TermFieldMatchData*, bool)
{
    return std::make_unique<queryeval::EmptySearch>();
}

std::unique_ptr<queryeval::SearchIterator>
EmptySearchContext::createFilterIterator(fef::TermFieldMatchData*, bool)
{
    return std::make_unique<queryeval::EmptySearch>();
}

}
