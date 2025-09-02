// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterators.h"
#include "element_id_extractor.h"

namespace search::queryeval {

RankedSearchIteratorBase::
RankedSearchIteratorBase(fef::TermFieldMatchDataArray matchData)
    : SearchIterator(),
      _matchData(std::move(matchData)),
      _needUnpack(1)
{ }

RankedSearchIteratorBase::~RankedSearchIteratorBase() = default;

void
RankedSearchIteratorBase::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    unpack(docid);
    if (_matchData.valid()) {
        ElementIdExtractor::get_element_ids(*_matchData[0], docid, element_ids);
    }
}

void
RankedSearchIteratorBase::and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    unpack(docid);
    if (_matchData.valid()) {
        ElementIdExtractor::and_element_ids_into(*_matchData[0], docid, element_ids);
    } else {
        element_ids.clear();
    }
}

}
