// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diskindex.h"
#include <vespa/searchlib/queryeval/blueprint.h>

namespace search::diskindex {

/**
 * Blueprint implementation for term searching in a disk index.
 **/
class DiskTermBlueprint : public queryeval::SimpleLeafBlueprint
{
private:
    queryeval::FieldSpecBase         _field;
    const DiskIndex               &  _diskIndex;
    DiskIndex::LookupResult::UP      _lookupRes;
    bool                             _useBitVector;
    bool                             _fetchPostingsDone;
    bool                             _hasEquivParent;
    index::PostingListHandle::UP     _postingHandle;
    BitVector::UP                    _bitVector;

public:
    /**
     * Create a new blueprint.
     *
     * @param field        the field to search in.
     * @param diskIndex    the disk index used to read the bit vector or posting list.
     * @param lookupRes    the result after disk dictionary lookup.
     * @param useBitVector whether or not we should use bit vector.
     **/
    DiskTermBlueprint(const queryeval::FieldSpecBase & field,
                      const DiskIndex & diskIndex,
                      DiskIndex::LookupResult::UP lookupRes,
                      bool useBitVector);

    // Inherit doc from Blueprint.
    // For now, this DiskTermBlueprint instance must have longer lifetime than the created iterator.
    std::unique_ptr<queryeval::SearchIterator> createLeafSearch(const fef::TermFieldMatchDataArray & tfmda, bool strict) const override;

    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override;
};

}
