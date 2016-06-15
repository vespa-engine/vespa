// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "postingchange.h"
#include "multivaluemapping.h"
#include "postinglistattribute.h"
#include <vespa/searchlib/common/bitvector.h>
#include <map>

namespace search {

namespace
{

void
removeDupAdditions(PostingChange<AttributePosting>::A &additions)
{
    typedef PostingChange<AttributePosting>::A::iterator Iterator;
    if (additions.empty())
        return;
    if (additions.size() == 1)
        return;
    std::sort(additions.begin(), additions.end());
    Iterator i = additions.begin();
    Iterator ie = additions.end();
    Iterator d = i;
    for (++i; i != ie; ++i, ++d) {
        if (d->_key == i->_key)
            break;
    }
    if (i == ie)
        return;		// no dups found
    for (++i; i != ie; ++i) {
        if (d->_key != i->_key) {
            ++d;
            *d = *i;
        }
    }
    additions.resize(d - additions.begin() + 1);
}


void
removeDupAdditions(PostingChange<AttributeWeightPosting>::A &additions)
{
    typedef PostingChange<AttributeWeightPosting>::A::iterator Iterator;
    if (additions.empty())
        return;
    if (additions.size() == 1u)
        return;
    std::sort(additions.begin(), additions.end());
    Iterator i = additions.begin();
    Iterator ie = additions.end();
    Iterator d = i;
    for (++i; i != ie; ++i, ++d) {
        if (d->_key == i->_key)
            break;
    }
    if (i == ie)
        return;		// no dups found
    // sum weights together
    d->setData(d->getData() + i->getData());
    for (++i; i != ie; ++i) {
        if (d->_key != i->_key) {
            ++d;
            *d = *i;
        } else {
            // sum weights together
            d->setData(d->getData() + i->getData());
        }
    }
    additions.resize(d - additions.begin() + 1);
}

void
removeDupRemovals(std::vector<uint32_t> &removals)
{
    typedef std::vector<uint32_t>::iterator Iterator;
    if (removals.empty())
        return;
    if (removals.size() == 1u)
        return;
    std::sort(removals.begin(), removals.end());
    Iterator i = removals.begin();
    Iterator ie = removals.end();
    Iterator d = i;
    for (++i; i != ie; ++i, ++d) {
        if (*d == *i)
            break;
    }
    if (i == ie)
        return;		// no dups found
    for (++i; i != ie; ++i) {
        if (*d != *i) {
            ++d;
            *d = *i;
        }
    }
    removals.resize(d - removals.begin() + 1);
}

}

EnumStoreBase::Index
EnumIndexMapper::map(EnumStoreBase::Index original, const EnumStoreComparator & compare) const
{
    (void) compare;
    return original;
}

template <>
void
PostingChange<AttributePosting>::removeDups(void)
{
    removeDupAdditions(_additions);
    removeDupRemovals(_removals);
}


template <>
void
PostingChange<AttributeWeightPosting>::removeDups(void)
{
    removeDupAdditions(_additions);
    removeDupRemovals(_removals);
}


template <typename P>
void
PostingChange<P>::apply(GrowableBitVector &bv)
{
    P *a = &_additions[0];
    P *ae = &_additions[0] + _additions.size();
    uint32_t *r = &_removals[0];
    uint32_t *re = &_removals[0] + _removals.size();

    while (a != ae || r != re) {
        if (r != re && (a == ae || *r < a->_key)) {
            // remove
            assert(*r < bv.size());
            bv.slowClearBit(*r);
            ++r;
        } else {
            if (r != re && !(a->_key < *r)) {
                // update or add
                assert(a->_key < bv.size());
                bv.slowSetBit(a->_key);
                ++r;
            } else {
                assert(a->_key < bv.size());
                bv.slowSetBit(a->_key);
            }
            ++a;
        }
    }
}

template <typename WeightedIndex>
class ActualChangeComputer {
public:
    typedef std::vector<WeightedIndex> V;
    void compute(const WeightedIndex * entriesNew, size_t szNew,
                 const WeightedIndex * entriesOld, size_t szOld,
                 V & added, V & changed, V & removed) const;
private:
    mutable V _oldEntries;
    mutable V _newEntries;
};

template <typename WeightedIndex>
void
ActualChangeComputer<WeightedIndex>::compute(const WeightedIndex * entriesNew, size_t szNew,
                                             const WeightedIndex * entriesOld, size_t szOld,
                                             V & added, V & changed, V & removed) const
{
    _newEntries.reserve(szNew);
    _oldEntries.reserve(szOld);
    _newEntries.clear();
    _oldEntries.clear();
    _newEntries.insert(_newEntries.begin(), entriesNew, entriesNew + szNew);
    _oldEntries.insert(_oldEntries.begin(), entriesOld, entriesOld + szOld);
    std::sort(_newEntries.begin(), _newEntries.end());
    std::sort(_oldEntries.begin(), _oldEntries.end());
    auto newIt(_newEntries.begin()), oldIt(_oldEntries.begin());
    while (newIt != _newEntries.end() && oldIt != _oldEntries.end()) {
        if (newIt->value() == oldIt->value()) {
            if (newIt->weight() != oldIt->weight()) {
                changed.push_back(*newIt);
            }
            newIt++, oldIt++;
        } else if (newIt->value() < oldIt->value()) {
            added.push_back(*newIt++);
        } else {
            removed.push_back(*oldIt++);
        }
    }
    added.insert(added.end(), newIt, _newEntries.end());
    removed.insert(removed.end(), oldIt, _oldEntries.end());
}

template <typename WeightedIndex, typename PostingMap>
template <typename MultivalueMapping>
PostingMap
PostingChangeComputerT<WeightedIndex, PostingMap>::
compute(const MultivalueMapping & mvm, const DocIndices & docIndices,
        const EnumStoreComparator & compare, const EnumIndexMapper & mapper)
{
    typedef ActualChangeComputer<WeightedIndex> AC;
    AC actualChange;
    typename AC::V added, changed, removed;
    PostingMap changePost;
    
    // generate add postings and remove postings
    for (const auto & docIndex : docIndices) {
        const WeightedIndex * oldIndices = NULL;
        uint32_t valueCount = mvm.get(docIndex.first, oldIndices);
        added.clear(), changed.clear(), removed.clear();
        actualChange.compute(&docIndex.second[0], docIndex.second.size(), oldIndices, valueCount,
                             added, changed, removed);
        for (const WeightedIndex & wi : added) {
            changePost[EnumPostingPair(mapper.map(wi.value(), compare), &compare)].add(docIndex.first, wi.weight());
        }
        for (const WeightedIndex & wi : removed) {
            changePost[EnumPostingPair(mapper.map(wi.value(), compare), &compare)].remove(docIndex.first);
        }
        for (const WeightedIndex & wi : changed) {
            changePost[EnumPostingPair(mapper.map(wi.value(), compare), &compare)].remove(docIndex.first).add(docIndex.first, wi.weight());
        }
    }
    return changePost;
}

template class PostingChange<AttributePosting>;

template class PostingChange<AttributeWeightPosting>;

typedef PostingChange<btree::BTreeKeyData<unsigned int, int> > WeightedPostingChange;
typedef std::map<EnumPostingPair, WeightedPostingChange> WeightedPostingChangeMap;
typedef EnumStoreBase::Index EnumIndex;
typedef multivalue::WeightedValue<EnumIndex> WeightedIndex; 
typedef multivalue::Value<EnumIndex> ValueIndex; 

typedef MultiValueMappingT<WeightedIndex, multivalue::Index32> NormalWeightedMultiValueMapping;
typedef MultiValueMappingT<WeightedIndex, multivalue::Index64> HugeWeightedMultiValueMapping;
typedef MultiValueMappingT<ValueIndex, multivalue::Index32> NormalValueMultiValueMapping;
typedef MultiValueMappingT<ValueIndex, multivalue::Index64> HugeValueMultiValueMapping;
typedef std::vector<std::pair<uint32_t, std::vector<WeightedIndex>>> DocIndicesWeighted;
typedef std::vector<std::pair<uint32_t, std::vector<ValueIndex>>> DocIndicesValue;

template WeightedPostingChangeMap PostingChangeComputerT<WeightedIndex, WeightedPostingChangeMap>
             ::compute<NormalWeightedMultiValueMapping>(const NormalWeightedMultiValueMapping &,
                                                        const DocIndicesWeighted &,
                                                        const EnumStoreComparator &,
                                                        const EnumIndexMapper &);

template WeightedPostingChangeMap PostingChangeComputerT<WeightedIndex, WeightedPostingChangeMap>
             ::compute<HugeWeightedMultiValueMapping>(const HugeWeightedMultiValueMapping &,
                                                      const DocIndicesWeighted &,
                                                      const EnumStoreComparator &,
                                                      const EnumIndexMapper &);
                                       
template WeightedPostingChangeMap PostingChangeComputerT<ValueIndex, WeightedPostingChangeMap>
             ::compute<NormalValueMultiValueMapping>(const NormalValueMultiValueMapping &,
                                                     const DocIndicesValue &,
                                                     const EnumStoreComparator &,
                                                     const EnumIndexMapper &);

template WeightedPostingChangeMap PostingChangeComputerT<ValueIndex, WeightedPostingChangeMap>
             ::compute<HugeValueMultiValueMapping>(const HugeValueMultiValueMapping &,
                                                   const DocIndicesValue &,
                                                   const EnumStoreComparator &,
                                                   const EnumIndexMapper &);

}
