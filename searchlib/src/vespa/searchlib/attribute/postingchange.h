// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/array.h>
#include "postinglisttraits.h"
#include "enumstorebase.h"

namespace search
{

class GrowableBitVector;

/**
 * Class representing changes to a posting list for a single value.
 */
template <typename P>
class PostingChange
{
public:
    typedef vespalib::Array<P> A;
    typedef std::vector<uint32_t> R;
    A _additions;
    R _removals;

    PostingChange();
    ~PostingChange();
    inline void add(uint32_t docId, int32_t weight);

    PostingChange & remove(uint32_t docId) {
        _removals.push_back(docId);
        return *this;
    }

    void clear() {
        _additions.clear();
        _removals.clear();
    }

    /*
     * Remove duplicates in additions and removals vectors, since new
     * posting list tree doesn't support duplicate entries.
     */
    void removeDups();
    void apply(GrowableBitVector &bv);
};

class EnumIndexMapper
{
public:
    virtual ~EnumIndexMapper() { }
    virtual EnumStoreBase::Index map(EnumStoreBase::Index original, const EnumStoreComparator & compare) const;
    virtual bool hasFold() const { return false; }
};

template <typename WeightedIndex, typename PostingMap>
class PostingChangeComputerT
{
private:
    typedef std::vector<std::pair<uint32_t, std::vector<WeightedIndex>>> DocIndices;
public:
    template <typename MultivalueMapping>
    static PostingMap compute(const MultivalueMapping & mvm, const DocIndices & docIndices,
                              const EnumStoreComparator & compare, const EnumIndexMapper & mapper);
};

template <>
inline void
PostingChange<AttributePosting>::add(uint32_t docId, int32_t weight)
{
    (void) weight;
    _additions.push_back(AttributePosting(docId,
                                 btree::BTreeNoLeafData()));
}


template <>
inline void
PostingChange<AttributeWeightPosting>::add(uint32_t docId, int32_t weight)
{
    _additions.push_back(AttributeWeightPosting(docId, weight));
}


} // namespace search




