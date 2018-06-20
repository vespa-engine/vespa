// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "enumcomparator.h"

#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreebuilder.hpp>
#include <vespa/searchlib/btree/btree.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/array.hpp>

namespace search {

template <typename EntryType>
void EnumStoreT<EntryType>::freeUnusedEnum(Index idx, IndexSet & unused)
{
    Entry e = getEntry(idx);
    if (e.getRefCount() == 0) {
        Type value = e.getValue();
        if (unused.insert(idx).second) {
            _store.incDead(idx.bufferId(), getEntrySize(value));
        }
    }
}

template <typename EntryType>
void
EnumStoreT<EntryType>::
insertEntry(char * dst, uint32_t enumValue, uint32_t refCount, Type value)
{
    memcpy(dst, &enumValue, sizeof(uint32_t));
    uint32_t pos = sizeof(uint32_t);
    memcpy(dst + pos, &refCount, sizeof(uint32_t));
    pos += sizeof(uint32_t);
    insertEntryValue(dst + pos, value);
}

template <>
void
EnumStoreT<StringEntryType>::
insertEntryValue(char * dst, Type value);

template <typename EntryType>
void
EnumStoreT<EntryType>::printEntry(vespalib::asciistream & os, const Entry & e) const
{
    os << "Entry: {";
    os << "enum: " << e.getEnum();
    os << ", refcount: " << e.getRefCount();
    os << ", value: " << e.getValue();
    os << "}";
}

template <typename EntryType>
bool
EnumStoreT<EntryType>::getValue(Index idx, Type & value) const
{
    if (!validIndex(idx)) {
        return false;
    }
    value = getEntry(idx).getValue();
    return true;
}

template <typename EntryType>
void
EnumStoreT<EntryType>::printBuffer(vespalib::asciistream & os, uint32_t bufferIdx) const
{
    uint64_t i = 0;
    while (i < _store.getBufferState(bufferIdx).size()) {
        Index idx(i, bufferIdx);

        Entry e = this->getEntry(idx);
        this->printEntry(os, e);
        os << ", " << idx << '\n';
        i += this->getEntrySize(e.getValue());
    }
}

template <typename EntryType>
EnumStoreT<EntryType>::Builder::Builder()
    : _uniques(),
      _bufferSize(Index::align(1))
{ }

template <typename EntryType>
EnumStoreT<EntryType>::Builder::~Builder() { }

template <typename EntryType>
void
EnumStoreT<EntryType>::printValue(vespalib::asciistream & os, Index idx) const
{
    os << getValue(idx);
}

template <typename EntryType>
void
EnumStoreT<EntryType>::printValue(vespalib::asciistream & os, Type value) const
{
    os << value;
}


template <class EntryType>
void
EnumStoreT<EntryType>::writeValues(BufferWriter &writer,
                                   const Index *idxs, size_t count) const
{
    size_t sz(EntryType::fixedSize());
    for (uint32_t i = 0; i < count; ++i) {
        Index idx = idxs[i];
        const char *src(_store.getBufferEntry<char>(idx.bufferId(),
                                                    idx.offset()) +
                        EntryBase::size());
        writer.write(src, sz);
    }
}


template <class EntryType>
ssize_t
EnumStoreT<EntryType>::deserialize(const void *src,
                                      size_t available,
                                      size_t &initSpace)
{
    (void) src;
    size_t sz(EntryType::fixedSize());
    if (available < sz)
        return -1;
    uint32_t entrySize(alignEntrySize(EntryBase::size() + sz));
    initSpace += entrySize;
    return sz;
}

template <class EntryType>
ssize_t
EnumStoreT<EntryType>::deserialize(const void *src,
                                      size_t available,
                                      Index &idx)
{
    size_t sz(EntryType::fixedSize());
    if (available < sz)
        return -1;
    uint32_t activeBufferId = _store.getActiveBufferId(TYPE_ID);
    datastore::BufferState & buffer = _store.getBufferState(activeBufferId);
    uint32_t entrySize(alignEntrySize(EntryBase::size() + sz));
    if (buffer.remaining() < entrySize) {
        HDR_ABORT("not enough space");
    }
    uint64_t offset = buffer.size();
    char *dst(_store.getBufferEntry<char>(activeBufferId, offset));
    memcpy(dst, &_nextEnum, sizeof(uint32_t));
    uint32_t pos = sizeof(uint32_t);
    uint32_t refCount(0);
    memcpy(dst + pos, &refCount, sizeof(uint32_t));
    pos += sizeof(uint32_t);
    memcpy(dst + pos, src, sz);
    buffer.pushed_back(entrySize);
    ++_nextEnum;

    if (idx.valid()) {
        assert(ComparatorType::compare(getValue(idx),
                                       Entry(dst).getValue()) < 0);
    }
    idx = Index(offset, activeBufferId);
    return sz;
}


template <class EntryType>
bool
EnumStoreT<EntryType>::foldedChange(const Index &idx1, const Index &idx2)
{
    int cmpres = FoldedComparatorType::compareFolded(getValue(idx1),
                                                     getValue(idx2));
    assert(cmpres <= 0);
    return cmpres < 0;
}


template <typename EntryType>
bool
EnumStoreT<EntryType>::findEnum(Type value,
                                   EnumStoreBase::EnumHandle &e) const
{
    ComparatorType cmp(*this, value);
    Index idx;
    if (_enumDict->findFrozenIndex(cmp, idx)) {
        e = idx.ref();
        return true;
    }
    return false;
}

template <typename EntryType>
bool
EnumStoreT<EntryType>::findIndex(Type value, Index &idx) const
{
    ComparatorType cmp(*this, value);
    return _enumDict->findIndex(cmp, idx);
}


template <typename EntryType>
void
EnumStoreT<EntryType>::freeUnusedEnums(bool movePostingIdx)
{
    ComparatorType cmp(*this);
    if (EntryType::hasFold() && movePostingIdx) {
        FoldedComparatorType fcmp(*this);
        _enumDict->freeUnusedEnums(cmp, &fcmp);
    } else {
        _enumDict->freeUnusedEnums(cmp, NULL);
    }
}


template <typename EntryType>
void
EnumStoreT<EntryType>::freeUnusedEnums(const IndexVector &toRemove)
{
    ComparatorType cmp(*this);
    if (EntryType::hasFold()) {
        FoldedComparatorType fcmp(*this);
        _enumDict->freeUnusedEnums(toRemove, cmp, &fcmp);
    } else {
        _enumDict->freeUnusedEnums(toRemove, cmp, NULL);
    }
}


template <typename EntryType>
template <typename Dictionary>
void
EnumStoreT<EntryType>::addEnum(Type value,
                                  Index &newIdx,
                                  Dictionary &dict)
{
    typedef typename Dictionary::Iterator DictionaryIterator;
    uint32_t entrySize = this->getEntrySize(value);
    uint32_t activeBufferId = _store.getActiveBufferId(TYPE_ID);
    datastore::BufferState & buffer = _store.getBufferState(activeBufferId);
#ifdef LOG_ENUM_STORE
    LOG(info,
        "addEnum(): buffer[%u]: capacity = %" PRIu64
        ", size = %" PRIu64 ", remaining = %" PRIu64
        ", dead = %" PRIu64 ", entrySize = %u",
        activeBufferId, buffer.capacity(),
        buffer.size(), buffer.remaining(),
        buffer._deadElems, entrySize);
#endif
    if (buffer.remaining() < entrySize) {
        HDR_ABORT("not enough space");
    }

    // check if already present
    ComparatorType cmp(*this, value);
    DictionaryIterator it(btree::BTreeNode::Ref(), dict.getAllocator());
    it.lower_bound(dict.getRoot(), Index(), cmp);
    if (it.valid() && !cmp(Index(), it.getKey())) {
        newIdx = it.getKey();
        return;
    }

    uint64_t offset = buffer.size();
    char * dst = _store.template getBufferEntry<char>(activeBufferId, offset);
    this->insertEntry(dst, this->_nextEnum++, 0, value);
    buffer.pushed_back(entrySize);
    assert(Index::pad(offset) == 0);
    newIdx = Index(offset, activeBufferId);

    // update tree with new index
    dict.insert(it, newIdx, typename Dictionary::DataType());

    // Copy posting list idx from next entry if same
    // folded value.
    // Only for string posting list attributes, i.e. dictionary has
    // data and entry type has folded compare.
    if (DictionaryIterator::hasData() && EntryType::hasFold()) {
        FoldedComparatorType foldCmp(*this);
        ++it;
        if (!it.valid() || foldCmp(newIdx, it.getKey()))
            return;  // Next entry does not use same posting list
        --it;
        --it;
        if (it.valid() && !foldCmp(it.getKey(), newIdx))
            return;  // Previous entry uses same posting list
        if (it.valid())
            ++it;
        else
            it.begin();
        assert(it.valid() && it.getKey() == newIdx);
        ++it;
        typename Dictionary::DataType pidx(it.getData());
        dict.thaw(it);
        it.writeData(typename Dictionary::DataType());
        --it;
        assert(it.valid() && it.getKey() == newIdx);
        it.writeData(pidx);
    }
}


template <typename EntryType>
void
EnumStoreT<EntryType>::addEnum(Type value, Index & newIdx)
{
    if (_enumDict->hasData())
        addEnum(value, newIdx,
                static_cast<EnumStoreDict<EnumPostingTree> *>(_enumDict)->
                getDictionary());
    else
        addEnum(value, newIdx,
                static_cast<EnumStoreDict<EnumTree> *>(_enumDict)->
                getDictionary());
}


template <typename DictionaryType>
struct TreeBuilderInserter {
    static void insert(typename DictionaryType::Builder & builder,
                       EnumStoreBase::Index enumIdx,
                       datastore::EntryRef postingIdx)
    {
        (void) postingIdx;
        builder.insert(enumIdx, typename DictionaryType::DataType());
    }
};

template <>
struct TreeBuilderInserter<EnumPostingTree> {
    static void insert(EnumPostingTree::Builder & builder,
                       EnumStoreBase::Index enumIdx,
                       datastore::EntryRef postingIdx)
    {
        builder.insert(enumIdx, postingIdx);
    }
};


template <typename EntryType>
template <typename Dictionary>
void
EnumStoreT<EntryType>::reset(Builder &builder, Dictionary &dict)
{
    typedef typename Dictionary::Builder DictionaryBuilder;
    EnumStoreBase::reset(builder.getBufferSize());

    DictionaryBuilder treeBuilder(dict.getAllocator());
    uint32_t activeBufferId = _store.getActiveBufferId(TYPE_ID);
    datastore::BufferState & state = _store.getBufferState(activeBufferId);

    // insert entries and update DictionaryBuilder
    const typename Builder::Uniques & uniques = builder.getUniques();
    for (typename Builder::Uniques::const_iterator iter = uniques.begin();
         iter != uniques.end(); ++iter)
    {
        uint64_t offset = state.size();
        Index idx(offset, activeBufferId);
        char * dst = _store.template getBufferEntry<char>(activeBufferId, offset);
        this->insertEntry(dst, this->_nextEnum++, iter->_refCount, iter->_value);
        state.pushed_back(iter->_sz);

        // update DictionaryBuilder with enum index and posting index
        TreeBuilderInserter<Dictionary>::insert(treeBuilder, idx, datastore::EntryRef(iter->_pidx));
    }

    // reset Dictionary
    dict.assign(treeBuilder); // destructive copy of treeBuilder
}


template <typename EntryType>
void
EnumStoreT<EntryType>::reset(Builder &builder)
{
    if (_enumDict->hasData())
        reset(builder,
              static_cast<EnumStoreDict<EnumPostingTree> *>(_enumDict)->
              getDictionary());
    else
        reset(builder,
              static_cast<EnumStoreDict<EnumTree> *>(_enumDict)->
              getDictionary());
}


template <typename EntryType>
template <typename Dictionary>
void
EnumStoreT<EntryType>::performCompaction(Dictionary &dict)
{
    typedef typename Dictionary::Iterator DictionaryIterator;
    uint32_t freeBufferIdx = _store.getActiveBufferId(TYPE_ID);
    datastore::BufferState & freeBuf = _store.getBufferState(freeBufferIdx);
    bool disabledReEnumerate = _disabledReEnumerate;

    uint32_t newEnum = 0;
    // copy entries from active buffer to free buffer
    for (DictionaryIterator iter = dict.begin(); iter.valid(); ++iter) {
        Index activeIdx = iter.getKey();

        Entry e = this->getEntry(activeIdx);

        // At this point the tree shal never reference any empy stuff.
        assert(e.getRefCount() > 0);
#ifdef LOG_ENUM_STORE
        LOG(info, "performCompaction(): copy entry: enum = %u, refCount = %u, value = %s",
            e.getEnum(), e.getRefCount(), e.getValue());
#endif
        Type value = e.getValue();
        uint32_t refCount = e.getRefCount();
        uint32_t oldEnum = e.getEnum();
        uint32_t entrySize = this->getEntrySize(value);
        if (disabledReEnumerate) {
            newEnum = oldEnum; // use old enum value
        }

        uint64_t offset = freeBuf.size();
        char * dst = _store.template getBufferEntry<char>(freeBufferIdx, offset);
        // insert entry into free buffer
        this->insertEntry(dst, newEnum, refCount, value);
#ifdef LOG_ENUM_STORE
        LOG(info, "performCompaction(): new entry: enum = %u, refCount = %u, value = %s", newEnum, 0, value);
#endif
        if (!disabledReEnumerate) {
            ++newEnum;
        }
        freeBuf.pushed_back(entrySize);
        assert(Index::pad(offset) == 0);
        Index newIdx = Index(offset, freeBufferIdx);
#ifdef LOG_ENUM_STORE
        LOG(info,
            "performCompaction(): new index: offset = %" PRIu64
            ", bufferIdx = %u",
            offset, freeBufferIdx);
#endif

        // update tree with new index
        std::atomic_thread_fence(std::memory_order_release);
        iter.writeKey(newIdx);

        // update index map with new index
        this->_indexMap[oldEnum] = newIdx;
    }
    if (disabledReEnumerate) {
        newEnum = this->_nextEnum; // use old range of enum values
    }
    this->postCompact(newEnum);
}


template <typename EntryType>
bool
EnumStoreT<EntryType>::performCompaction(uint64_t bytesNeeded)
{
    if ( ! this->preCompact(bytesNeeded) ) {
        return false;
    }
    if (_enumDict->hasData())
        performCompaction(static_cast<EnumStoreDict<EnumPostingTree> *>
                          (_enumDict)->getDictionary());
    else
        performCompaction(static_cast<EnumStoreDict<EnumTree> *>
                          (_enumDict)->getDictionary());
    return true;
}


template <typename EntryType>
template <typename Dictionary>
void
EnumStoreT<EntryType>::printCurrentContent(vespalib::asciistream &os,
                                              const Dictionary &dict) const
{
    typedef typename Dictionary::ConstIterator DictionaryConstIterator;

    for (DictionaryConstIterator iter = dict.begin(); iter.valid(); ++iter) {
        Index idx = iter.getKey();
        if (!this->validIndex(idx)) {
            os << "Bad entry: " << idx << '\n';
        } else {
            Entry e = this->getEntry(idx);
            this->printEntry(os, e);
            os << ", " << idx << '\n';
        }
    }
}


template <typename EntryType>
void
EnumStoreT<EntryType>::printCurrentContent(vespalib::asciistream &os) const
{
    if (_enumDict->hasData())
        printCurrentContent(os,
                            static_cast<EnumStoreDict<EnumPostingTree> *>
                            (_enumDict)->getDictionary());
    else
        printCurrentContent(os,
                            static_cast<EnumStoreDict<EnumTree> *>
                            (_enumDict)->getDictionary());
}

} // namespace search

