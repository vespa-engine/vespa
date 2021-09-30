// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include "postingdata.h"
#include <vespa/vespalib/util/array.h>

namespace vespalib::datastore { class EntryComparator; }

namespace search {

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
};

class EnumIndexMapper
{
public:
    virtual ~EnumIndexMapper() { }
    virtual IEnumStore::Index map(IEnumStore::Index original) const;
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
                              const vespalib::datastore::EntryComparator & compare, const EnumIndexMapper & mapper);
};

template <>
inline void
PostingChange<AttributePosting>::add(uint32_t docId, int32_t weight)
{
    (void) weight;
    _additions.push_back(AttributePosting(docId,
                                 vespalib::btree::BTreeNoLeafData()));
}


template <>
inline void
PostingChange<AttributeWeightPosting>::add(uint32_t docId, int32_t weight)
{
    _additions.push_back(AttributeWeightPosting(docId, weight));
}

} // namespace search




