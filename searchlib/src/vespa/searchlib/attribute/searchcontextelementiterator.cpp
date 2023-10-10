// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchcontextelementiterator.h"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

using search::fef::TermFieldMatchDataPosition;

namespace search::attribute {

void
SearchContextElementIterator::getElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) {
    int32_t weight(0);
    for (int32_t id = _searchContext.find(docId, 0, weight); id >= 0; id = _searchContext.find(docId, id+1, weight)) {
        elementIds.push_back(id);
    }
}
void
SearchContextElementIterator::mergeElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) {
    size_t toKeep(0);
    int32_t id(-1);
    int32_t weight(0);
    for (int32_t candidate : elementIds) {
        if (candidate > id) {
            id = _searchContext.find(docId, candidate, weight);
            if (id < 0) break;
        }
        if (id == candidate) {
            elementIds[toKeep++] = candidate;
        }
    }
    elementIds.resize(toKeep);
}

SearchContextElementIterator::SearchContextElementIterator(queryeval::SearchIterator::UP search, const ISearchContext & sc)
    : ElementIterator(std::move(search)),
      _searchContext(sc)
{}

SearchContextElementIterator::~SearchContextElementIterator() = default;

}
