// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumhintsearchcontext.h"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search {

using queryeval::SearchIterator;

namespace attribute {

using btree::BTreeNode;
using fef::TermFieldMatchData;

EnumHintSearchContext::
EnumHintSearchContext(const EnumStoreDictBase &dictionary,
                      uint32_t docIdLimit,
                      uint64_t numValues)
    : _dictionary(dictionary),
      _frozenRootRef(dictionary.getFrozenRootRef()),
      _uniqueValues(0u),
      _docIdLimit(docIdLimit),
      _numValues(numValues)
{
}


EnumHintSearchContext::~EnumHintSearchContext()
{
}


void
EnumHintSearchContext::lookupTerm(const EnumStoreComparator &comp)
{
    _uniqueValues = _dictionary.lookupFrozenTerm(_frozenRootRef, comp);
}


void
EnumHintSearchContext::lookupRange(const EnumStoreComparator &low,
                                   const EnumStoreComparator &high)
{
    _uniqueValues = _dictionary.lookupFrozenRange(_frozenRootRef, low, high);
}

void
EnumHintSearchContext::fetchPostings(bool strict)
{
    (void) strict;
}

SearchIterator::UP
EnumHintSearchContext::createPostingIterator(TermFieldMatchData *matchData,
                                             bool strict)
{
    (void) matchData;
    (void) strict;

    return (_uniqueValues == 0u)
        ? SearchIterator::UP(new queryeval::EmptySearch())
        : SearchIterator::UP();
}


unsigned int
EnumHintSearchContext::approximateHits() const
{
    return (_uniqueValues == 0u)
        ? 0u
        : std::max(uint64_t(_docIdLimit), _numValues);
}

} // namespace attribute

} // namespace search
