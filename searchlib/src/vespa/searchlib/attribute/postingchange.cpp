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
    using EnumIndex = EnumStoreBase::Index;
    using V = std::vector<multivalue::WeightedValue<EnumIndex>>;
    using WIV = std::vector<WeightedIndex>;
    void compute(const WeightedIndex * entriesNew, size_t szNew,
                 const WeightedIndex * entriesOld, size_t szOld,
                 V & added, V & changed, V & removed);

    ActualChangeComputer(const EnumStoreComparator &compare,
                         const EnumIndexMapper &mapper)
        : _oldEntries(),
          _newEntries(),
          _cachedMapping(),
          _compare(compare),
          _mapper(mapper),
          _hasFold(mapper.hasFold())
    {
    }

private:
    WIV _oldEntries;
    WIV _newEntries;
    vespalib::hash_map<uint32_t, uint32_t> _cachedMapping;
    const EnumStoreComparator &_compare;
    const EnumIndexMapper &_mapper;
    const bool _hasFold;

    static void copyFast(WIV &dst, const WeightedIndex *src, size_t sz)
    {
        dst.insert(dst.begin(), src, src + sz);
    }

    EnumIndex mapEnumIndex(EnumIndex unmapped) {
        auto itr = _cachedMapping.insert(std::make_pair(unmapped.ref(), 0));
        if (itr.second) {
            itr.first->second = _mapper.map(unmapped, _compare).ref();
        }
        return EnumIndex(itr.first->second);
    }


    void copyMapped(WIV &dst, const WeightedIndex *src, size_t sz)
    {
        const WeightedIndex *srce = src + sz;
        for (const WeightedIndex *i = src; i < srce; ++i) {
            dst.emplace_back(mapEnumIndex(i->value()), i->weight());
        }
    }

    void copyEntries(WIV &dst, const WeightedIndex *src, size_t sz)
    {
        dst.reserve(sz);
        dst.clear();
        if (_hasFold) {
            copyMapped(dst, src, sz);
        } else {
            copyFast(dst, src, sz);
        }
        std::sort(dst.begin(), dst.end());
    }
};

template <typename WeightedIndex>
class MergeDupIterator {
    using InnerIter = typename std::vector<WeightedIndex>::const_iterator;
    using EnumIndex = EnumStoreBase::Index;
    using Entry = multivalue::WeightedValue<EnumIndex>;
    InnerIter _cur;
    InnerIter _end;
    Entry _entry;
    bool _valid;
    void merge() {
        EnumIndex idx = _cur->value();
        int32_t weight = _cur->weight();
        ++_cur;
        while (_cur != _end && _cur->value() == idx) {
            // sum weights together. Overflow is not handled.
            weight += _cur->weight();
            ++_cur;
        }
        _entry = Entry(idx, weight);
    }
public:
    MergeDupIterator(const std::vector<WeightedIndex> &vec)
        : _cur(vec.begin()),
          _end(vec.end()),
          _entry(),
          _valid(_cur != _end)
    {
        if (_valid) {
            merge();
        }
    }

    bool valid() const { return _valid; }
    const Entry &entry() const { return _entry; }
    EnumIndex value() const { return _entry.value(); }
    int32_t weight() const { return _entry.weight(); }
    void step() {
        if (_cur != _end) {
            merge();
        } else {
            _valid = false;
        }
    }
};

template <typename WeightedIndex>
void
ActualChangeComputer<WeightedIndex>::compute(const WeightedIndex * entriesNew, size_t szNew,
                                             const WeightedIndex * entriesOld, size_t szOld,
                                             V & added, V & changed, V & removed)
{
    copyEntries(_newEntries, entriesNew, szNew);
    copyEntries(_oldEntries, entriesOld, szOld);
    MergeDupIterator<WeightedIndex> oldIt(_oldEntries);
    MergeDupIterator<WeightedIndex> newIt(_newEntries);

    while (newIt.valid() && oldIt.valid()) {
        if (newIt.value() == oldIt.value()) {
            if (newIt.weight() != oldIt.weight()) {
                changed.push_back(newIt.entry());
            }
            newIt.step();
            oldIt.step();
        } else if (newIt.value() < oldIt.value()) {
            added.push_back(newIt.entry());
            newIt.step();
        } else {
            removed.push_back(oldIt.entry());
            oldIt.step();
        }
    }
    while (newIt.valid()) {
        added.push_back(newIt.entry());
        newIt.step();
    }
    while (oldIt.valid()) {
        removed.push_back(oldIt.entry());
        oldIt.step();
    }
}

template <typename WeightedIndex, typename PostingMap>
template <typename MultivalueMapping>
PostingMap
PostingChangeComputerT<WeightedIndex, PostingMap>::
compute(const MultivalueMapping & mvm, const DocIndices & docIndices,
        const EnumStoreComparator & compare, const EnumIndexMapper & mapper)
{
    typedef ActualChangeComputer<WeightedIndex> AC;
    AC actualChange(compare, mapper);
    typename AC::V added, changed, removed;
    PostingMap changePost;
    
    // generate add postings and remove postings
    for (const auto & docIndex : docIndices) {
        const WeightedIndex * oldIndices = NULL;
        uint32_t valueCount = mvm.get(docIndex.first, oldIndices);
        added.clear(), changed.clear(), removed.clear();
        actualChange.compute(&docIndex.second[0], docIndex.second.size(), oldIndices, valueCount,
                             added, changed, removed);
        for (const auto & wi : added) {
            changePost[EnumPostingPair(wi.value(), &compare)].add(docIndex.first, wi.weight());
        }
        for (const auto & wi : removed) {
            changePost[EnumPostingPair(wi.value(), &compare)].remove(docIndex.first);
        }
        for (const auto & wi : changed) {
            changePost[EnumPostingPair(wi.value(), &compare)].remove(docIndex.first).add(docIndex.first, wi.weight());
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
