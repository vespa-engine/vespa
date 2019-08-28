// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_dictionary.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/address_space.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <atomic>
#include <set>

namespace vespalib { class asciistream; }
namespace search {

class BufferWriter;

namespace attribute { class Status; }

using EnumStoreComparator = datastore::EntryComparator;


class EnumStoreBase : public IEnumStore {
public:
    using DataStoreType = datastore::DataStoreT<Index>;
    using generation_t = vespalib::GenerationHandler::generation_t;


    class EntryBase {
    protected:
        char * _data;
    public:
        EntryBase(void * data) : _data(static_cast<char *>(data)) {}

        uint32_t getRefCount() const {
            return *(reinterpret_cast<uint32_t *>(_data) + 1);
        }

        void incRefCount() {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            ++(*dst);
        }

        void decRefCount() {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            --(*dst);
        }

        void setRefCount(uint32_t refCount) {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            *dst = refCount;
        }

        static uint32_t size() { return 2*sizeof(uint32_t); }
    };

protected:

    class EnumBufferType : public datastore::BufferType<char> {
    private:
        size_t _minSizeNeeded; // lower cap for sizeNeeded
        size_t _deadElems;     // dead elements in active buffer
        bool   _pendingCompact;
        bool   _wantCompact;
    public:
        EnumBufferType();

        size_t calcArraysToAlloc(uint32_t bufferId, size_t sizeNeeded, bool resizing) const override;

        void setSizeNeededAndDead(size_t sizeNeeded, size_t deadElems) {
            _minSizeNeeded = sizeNeeded;
            _deadElems = deadElems;
        }

        void onFree(size_t usedElems) override {
            datastore::BufferType<char>::onFree(usedElems);
            _pendingCompact = _wantCompact;
            _wantCompact = false;
        }

        void setWantCompact() { _wantCompact = true; }
        bool getPendingCompact() const { return _pendingCompact; }
        void clearPendingCompact() { _pendingCompact = false; }
    };

    IEnumStoreDictionary    *_enumDict;
    DataStoreType         _store;
    EnumBufferType        _type;
    std::vector<uint32_t> _toHoldBuffers; // used during compaction

    static const uint32_t TYPE_ID = 0;

    EnumStoreBase(uint64_t initBufferSize, bool hasPostings);

    virtual ~EnumStoreBase();

    EntryBase getEntryBase(Index idx) const {
        return EntryBase(const_cast<DataStoreType &>(_store).getEntry<char>(idx));
    }
    datastore::BufferState & getBuffer(uint32_t bufferIdx) {
        return _store.getBufferState(bufferIdx);
    }
    const datastore::BufferState & getBuffer(uint32_t bufferIdx) const {
        return _store.getBufferState(bufferIdx);
    }
    bool validIndex(Index idx) const {
        return (idx.valid() && idx.offset() < _store.getBufferState(idx.bufferId()).size());
    }

    uint32_t getBufferIndex(datastore::BufferState::State status);
    void postCompact();
    bool preCompact(uint64_t bytesNeeded);

public:
    void reset(uint64_t initBufferSize);

    uint32_t getRefCount(Index idx) const { return getEntryBase(idx).getRefCount(); }
    void incRefCount(Index idx)           { getEntryBase(idx).incRefCount(); }
    void decRefCount(Index idx)           { getEntryBase(idx).decRefCount(); }
    
    // Only use when reading from enumerated attribute save files
    void fixupRefCount(Index idx, uint32_t refCount) override {
        getEntryBase(idx).setRefCount(refCount);
    } 
    
    uint32_t getNumUniques() const override { return _enumDict->getNumUniques(); }

    uint32_t getRemaining() const {
        return _store.getBufferState(_store.getActiveBufferId(TYPE_ID)).remaining();
    }
    uint32_t getCapacity() const {
        return _store.getBufferState(_store.getActiveBufferId(TYPE_ID)).capacity();
    }
    vespalib::MemoryUsage getMemoryUsage() const override;
    vespalib::MemoryUsage getTreeMemoryUsage() const override { return _enumDict->get_memory_usage(); }

    vespalib::AddressSpace getAddressSpaceUsage() const;

    void transferHoldLists(generation_t generation);
    void trimHoldLists(generation_t firstUsed);

    static void failNewSize(uint64_t minNewSize, uint64_t maxSize);

    // Align buffers and entries to 4 bytes boundary.
    static uint64_t alignBufferSize(uint64_t val) { return Index::align(val); }
    static uint32_t alignEntrySize(uint32_t val) { return Index::align(val); }

    void fallbackResize(uint64_t bytesNeeded);
    bool getPendingCompact() const { return _type.getPendingCompact(); }
    void clearPendingCompact() { _type.clearPendingCompact(); }

    virtual ssize_t deserialize(const void *src, size_t available, size_t &initSpace) = 0;
    virtual ssize_t deserialize(const void *src, size_t available, Index &idx) = 0;

    ssize_t deserialize0(const void *src, size_t available, IndexVector &idx) override;

    ssize_t deserialize(const void *src, size_t available, IndexVector &idx) {
        return _enumDict->deserialize(src, available, idx);
    }

    virtual void freeUnusedEnums(const IndexSet& toRemove) = 0;

    void fixupRefCounts(const EnumVector &hist) { _enumDict->fixupRefCounts(hist); }
    void freezeTree() { _enumDict->freeze(); }

    virtual bool performCompaction(uint64_t bytesNeeded, EnumIndexMap & old2New) = 0;

    IEnumStoreDictionary &getEnumStoreDict() override { return *_enumDict; }
    const IEnumStoreDictionary &getEnumStoreDict() const override { return *_enumDict; }
    EnumPostingTree &getPostingDictionary() { return _enumDict->getPostingDictionary(); }

    const EnumPostingTree &getPostingDictionary() const {
        return _enumDict->getPostingDictionary();
    }
    const datastore::DataStoreBase &get_data_store_base() const override { return _store; }
};

vespalib::asciistream & operator << (vespalib::asciistream & os, const IEnumStore::Index & idx);

extern template
class datastore::DataStoreT<IEnumStore::Index>;


}
