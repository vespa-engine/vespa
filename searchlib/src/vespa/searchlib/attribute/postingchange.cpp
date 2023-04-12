// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postingchange.h"
#include "multi_value_mapping.h"
#include "postinglistattribute.h"
#include <vespa/searchcommon/attribute/multivalue.h>
#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/array.hpp>

using vespalib::datastore::AtomicEntryRef;

namespace search {

namespace {

template <typename WeightedIndex>
struct CompareValue {
    bool operator()(const WeightedIndex& lhs, const WeightedIndex& rhs) const
    {
        return multivalue::get_value_ref(lhs).load_relaxed() < multivalue::get_value_ref(rhs).load_relaxed();
    };
};

void
removeDupAdditions(PostingChange<AttributePosting>::A &additions)
{
    using Iterator = PostingChange<AttributePosting>::A::iterator;
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
        return;     // no dups found
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
    using Iterator = PostingChange<AttributeWeightPosting>::A::iterator;
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
        return;     // no dups found
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
    using Iterator = std::vector<uint32_t>::iterator;
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
        return;     // no dups found
    for (++i; i != ie; ++i) {
        if (*d != *i) {
            ++d;
            *d = *i;
        }
    }
    removals.resize(d - removals.begin() + 1);
}

}

IEnumStore::Index
EnumIndexMapper::map(IEnumStore::Index original) const
{
    return original;
}

template <>
void
PostingChange<AttributePosting>::removeDups()
{
    removeDupAdditions(_additions);
    removeDupRemovals(_removals);
}


template <>
void
PostingChange<AttributeWeightPosting>::removeDups()
{
    removeDupAdditions(_additions);
    removeDupRemovals(_removals);
}

template <typename P>
PostingChange<P>::PostingChange() = default;
template <typename P>
PostingChange<P>::~PostingChange() = default;

template <typename WeightedIndex>
class ActualChangeComputer {
public:
    using EnumIndex = IEnumStore::Index;
    using AlwaysWeightedIndexVector = std::vector<multivalue::WeightedValue<AtomicEntryRef>>;
    using WeightedIndexVector = std::vector<WeightedIndex>;
    void compute(const WeightedIndex * entriesNew, size_t szNew,
                 const WeightedIndex * entriesOld, size_t szOld,
                 AlwaysWeightedIndexVector & added, AlwaysWeightedIndexVector & changed, AlwaysWeightedIndexVector & removed);

    ActualChangeComputer(const vespalib::datastore::EntryComparator& compare, const EnumIndexMapper& mapper);
    ~ActualChangeComputer();

private:
    WeightedIndexVector _oldEntries;
    WeightedIndexVector _newEntries;
    vespalib::hash_map<uint32_t, uint32_t> _cachedMapping;
    const vespalib::datastore::EntryComparator &_compare;
    const EnumIndexMapper &_mapper;
    const bool _hasFold;

    static void copyFast(WeightedIndexVector &dst, const WeightedIndex *src, size_t sz)
    {
        dst.insert(dst.begin(), src, src + sz);
    }

    EnumIndex mapEnumIndex(EnumIndex unmapped) {
        auto itr = _cachedMapping.insert(std::make_pair(unmapped.ref(), 0));
        if (itr.second) {
            itr.first->second = _mapper.map(unmapped).ref();
        }
        return EnumIndex(vespalib::datastore::EntryRef(itr.first->second));
    }


    void copyMapped(WeightedIndexVector &dst, const WeightedIndex *src, size_t sz)
    {
        const WeightedIndex *srce = src + sz;
        for (const WeightedIndex *i = src; i < srce; ++i) {
            dst.emplace_back(multivalue::ValueBuilder<WeightedIndex>::build(AtomicEntryRef(mapEnumIndex(multivalue::get_value_ref(*i).load_relaxed())), multivalue::get_weight(*i)));
        }
    }

    void copyEntries(WeightedIndexVector &dst, const WeightedIndex *src, size_t sz)
    {
        dst.reserve(sz);
        dst.clear();
        if (_hasFold) {
            copyMapped(dst, src, sz);
        } else {
            copyFast(dst, src, sz);
        }
        std::sort(dst.begin(), dst.end(), CompareValue<WeightedIndex>());
    }
};

template <typename WeightedIndex>
class MergeDupIterator {
    using InnerIter = typename std::vector<WeightedIndex>::const_iterator;
    using EnumIndex = IEnumStore::Index;
    using Entry = multivalue::WeightedValue<AtomicEntryRef>;
    InnerIter _cur;
    InnerIter _end;
    Entry _entry;
    bool _valid;
    void merge() {
        EnumIndex idx = multivalue::get_value_ref(*_cur).load_relaxed();
        int32_t weight = multivalue::get_weight(*_cur);
        ++_cur;
        while (_cur != _end && multivalue::get_value_ref(*_cur).load_relaxed() == idx) {
            // sum weights together. Overflow is not handled.
            weight += multivalue::get_weight(*_cur);
            ++_cur;
        }
        _entry = Entry(AtomicEntryRef(idx), weight);
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
    EnumIndex value() const { return _entry.value_ref().load_relaxed(); }
    int32_t weight() const { return _entry.weight(); }
    void next() {
        if (_cur != _end) {
            merge();
        } else {
            _valid = false;
        }
    }
};

template <typename WeightedIndex>
ActualChangeComputer<WeightedIndex>::ActualChangeComputer(const vespalib::datastore::EntryComparator &compare,
                                                          const EnumIndexMapper &mapper)
    : _oldEntries(),
      _newEntries(),
      _cachedMapping(),
      _compare(compare),
      _mapper(mapper),
      _hasFold(mapper.hasFold())
{ }

template <typename WeightedIndex>
ActualChangeComputer<WeightedIndex>::~ActualChangeComputer() = default;

template <typename WeightedIndex>
void
ActualChangeComputer<WeightedIndex>::compute(const WeightedIndex * entriesNew, size_t szNew,
                                             const WeightedIndex * entriesOld, size_t szOld,
                                             AlwaysWeightedIndexVector & added,
                                             AlwaysWeightedIndexVector & changed,
                                             AlwaysWeightedIndexVector & removed)
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
            newIt.next();
            oldIt.next();
        } else if (newIt.value() < oldIt.value()) {
            added.push_back(newIt.entry());
            newIt.next();
        } else {
            removed.push_back(oldIt.entry());
            oldIt.next();
        }
    }
    while (newIt.valid()) {
        added.push_back(newIt.entry());
        newIt.next();
    }
    while (oldIt.valid()) {
        removed.push_back(oldIt.entry());
        oldIt.next();
    }
}

template <typename WeightedIndex, typename PostingMap>
template <typename MultivalueMapping>
PostingMap
PostingChangeComputerT<WeightedIndex, PostingMap>::
compute(const MultivalueMapping & mvm, const DocIndices & docIndices,
        const vespalib::datastore::EntryComparator & compare, const EnumIndexMapper & mapper)
{
    using AC = ActualChangeComputer<WeightedIndex>;
    AC actualChange(compare, mapper);
    typename AC::AlwaysWeightedIndexVector added, changed, removed;
    PostingMap changePost;
    
    // generate add postings and remove postings
    for (const auto & docIndex : docIndices) {
        vespalib::ConstArrayRef<WeightedIndex> oldIndices(mvm.get(docIndex.first));
        added.clear(), changed.clear(), removed.clear();
        actualChange.compute(docIndex.second.data(), docIndex.second.size(), oldIndices.data(), oldIndices.size(),
                             added, changed, removed);
        for (const auto & wi : added) {
            changePost[EnumPostingPair(wi.value_ref().load_relaxed(), &compare)].add(docIndex.first, wi.weight());
        }
        for (const auto & wi : removed) {
            changePost[EnumPostingPair(wi.value_ref().load_relaxed(), &compare)].remove(docIndex.first);
        }
        for (const auto & wi : changed) {
            changePost[EnumPostingPair(wi.value_ref().load_relaxed(), &compare)].remove(docIndex.first).add(docIndex.first, wi.weight());
        }
    }
    return changePost;
}

template class PostingChange<AttributePosting>;

template class PostingChange<AttributeWeightPosting>;

using WeightedPostingChange = PostingChange<vespalib::btree::BTreeKeyData<unsigned int, int> >;
using WeightedPostingChangeMap = std::map<EnumPostingPair, WeightedPostingChange>;
using WeightedIndex = multivalue::WeightedValue<AtomicEntryRef>;
using ValueIndex = AtomicEntryRef;

using WeightedMultiValueMapping = attribute::MultiValueMapping<WeightedIndex>;
using ValueMultiValueMapping = attribute::MultiValueMapping<ValueIndex>;
using DocIndicesWeighted = std::vector<std::pair<uint32_t, std::vector<WeightedIndex>>>;
using DocIndicesValue = std::vector<std::pair<uint32_t, std::vector<ValueIndex>>>;

template WeightedPostingChangeMap PostingChangeComputerT<WeightedIndex, WeightedPostingChangeMap>
             ::compute<WeightedMultiValueMapping>(const WeightedMultiValueMapping &,
                                                   const DocIndicesWeighted &,
                                                   const vespalib::datastore::EntryComparator &,
                                                   const EnumIndexMapper &);

template WeightedPostingChangeMap PostingChangeComputerT<ValueIndex, WeightedPostingChangeMap>
             ::compute<ValueMultiValueMapping>(const ValueMultiValueMapping &,
                                                const DocIndicesValue &,
                                                const vespalib::datastore::EntryComparator &,
                                                const EnumIndexMapper &);

}

namespace vespalib {

template class Array<search::AttributePosting>;
template class Array<search::AttributeWeightPosting>;

}
