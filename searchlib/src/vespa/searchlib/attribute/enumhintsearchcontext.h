// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "postinglisttraits.h"
#include "ipostinglistsearchcontext.h"
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search
{

namespace attribute
{

/**
 * Search context helper for enumerated attributes, used to eliminate
 * searches for values that are not present at all.
 */

class EnumHintSearchContext : public IPostingListSearchContext
{
    const EnumStoreDictBase  &_dictionary;
    const btree::BTreeNode::Ref _frozenRootRef;
    uint32_t                _uniqueValues;
    uint32_t                _docIdLimit;
    uint64_t                _numValues; // attr.getStatus().getNumValues();
    
protected:
    EnumHintSearchContext(const EnumStoreDictBase &dictionary,
                          uint32_t docIdLimit,
                          uint64_t numValues);
    ~EnumHintSearchContext();

    void lookupTerm(const EnumStoreComparator &comp);
    void lookupRange(const EnumStoreComparator &low, const EnumStoreComparator &high);
    
    queryeval::SearchIterator::UP
    createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) override;

    void fetchPostings(bool strict) override;
    unsigned int approximateHits() const override;
};


} // namespace attribute

} // namespace search

