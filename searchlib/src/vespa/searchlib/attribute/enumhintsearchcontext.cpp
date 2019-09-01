// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumhintsearchcontext.h"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

using queryeval::SearchIterator;
using btree::BTreeNode;
using fef::TermFieldMatchData;

EnumHintSearchContext::
EnumHintSearchContext(const IEnumStoreDictionary &dictionary,
                      uint32_t docIdLimit,
                      uint64_t numValues)
    : _dict_snapshot(dictionary.get_read_snapshot()),
      _uniqueValues(0u),
      _docIdLimit(docIdLimit),
      _numValues(numValues)
{
}


EnumHintSearchContext::~EnumHintSearchContext() = default;


void
EnumHintSearchContext::lookupTerm(const datastore::EntryComparator &comp)
{
    _uniqueValues = _dict_snapshot->count(comp);
}


void
EnumHintSearchContext::lookupRange(const datastore::EntryComparator &low,
                                   const datastore::EntryComparator &high)
{
    _uniqueValues = _dict_snapshot->count_in_range(low, high);
}

void
EnumHintSearchContext::fetchPostings(bool strict)
{
    (void) strict;
}

SearchIterator::UP
EnumHintSearchContext::createPostingIterator(TermFieldMatchData *, bool )
{
    return (_uniqueValues == 0u)
        ? std::make_unique<queryeval::EmptySearch>()
        : SearchIterator::UP();
}


unsigned int
EnumHintSearchContext::approximateHits() const
{
    return (_uniqueValues == 0u)
        ? 0u
        : std::max(uint64_t(_docIdLimit), _numValues);
}

}
