// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexing_search.h"

#include <algorithm>

namespace search::queryeval {
void
IndexingSearch::doSeek(uint32_t docid)
{
    setDocId(docid);
}

void
IndexingSearch::doUnpack(uint32_t)
{
}

void
IndexingSearch::or_hits_into(BitVector &result, uint32_t begin_id)
{
    result.setInterval(begin_id, getEndId());
}

void
IndexingSearch::and_hits_into(BitVector &, uint32_t)
{
    // nop
}

BitVector::UP
IndexingSearch::get_hits(uint32_t begin_id)
{
    auto result = BitVector::create(begin_id, getEndId());
    result->setInterval(begin_id, getEndId());
    return result;
}

IndexingSearch::IndexingSearch(uint32_t element_id)
    : SearchIterator(),
      _element_id(element_id) {
}

IndexingSearch::~IndexingSearch() = default;

void
IndexingSearch::get_element_ids(uint32_t /*docid*/, std::vector<uint32_t>& element_ids)
{
    element_ids.push_back(_element_id);
}

void
IndexingSearch::and_element_ids_into(uint32_t /*docid*/, std::vector<uint32_t>& element_ids)
{
    // Are the elements in element_ids sorted? Could use binary search
    bool contained = std::find(element_ids.begin(), element_ids.end(), _element_id) != element_ids.end();
    element_ids.clear();
    if (contained) {
        element_ids.push_back(_element_id);
    }

}

} // namespace
