// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchcontextelementiterator.h"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

using search::fef::TermFieldMatchDataPosition;

namespace search::attribute {

void
SearchContextElementIterator::getElementIds(uint32_t docId, std::vector<uint32_t> & elementIds)
{
    _search->get_element_ids(docId, elementIds);
}

void
SearchContextElementIterator::mergeElementIds(uint32_t docId, std::vector<uint32_t> & elementIds)
{
    _search->and_element_ids_into(docId, elementIds);
}

SearchContextElementIterator::SearchContextElementIterator(queryeval::SearchIterator::UP search, const ISearchContext & sc)
    : ElementIterator(std::move(search)),
      _searchContext(sc)
{}

SearchContextElementIterator::~SearchContextElementIterator() = default;

}
