// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastore.h"
#include "entryref.h"
#include "unique_store_add_result.h"
#include "unique_store_entry.h"
#include "i_compactable.h"
#include <cassert>

namespace search::datastore {

namespace string_allocator {

extern std::vector<size_t> array_sizes;
uint32_t get_type_id(size_t string_len);

};

/*
 * Entry type for small strings. array_size is passed to constructors and
 * clean_hold to tell how many bytes are set aside for the entry.
 * array_size is supposed to be > sizeof(UniqueStoreSmallStringEntry).
 *
 * Class is trivially destructable, i.e. no need to call destructor.
 * Class is trivially copyable, i.e. memcpy can be used to make a copy.
 */
class UniqueStoreSmallStringEntry : public UniqueStoreEntryBase {
    char _value[0];
public:
    constexpr UniqueStoreSmallStringEntry()
        : UniqueStoreEntryBase(),
          _value()
    { }
    
    UniqueStoreSmallStringEntry(const char *value, size_t value_len, size_t array_size)
        : UniqueStoreEntryBase()
    {
        assert(value_offset() + value_len < array_size);
        memcpy(&_value[0], value, value_len);
        memset(&_value[0] + value_len, 0, array_size - value_len - value_offset());
    }

    void clean_hold(size_t array_size) {
        memset(&_value[0], 0, array_size - value_offset());
    }

    const char *value() const { return &_value[0]; }
    size_t value_offset() const { return &_value[0] - reinterpret_cast<const char *>(this); }
};

/*
 * Buffer type for small strings in unique store. Each entry uses array_size
 * bytes
 */
class UniqueStoreSmallStringBufferType : public BufferType<char> {
public:
    UniqueStoreSmallStringBufferType(uint32_t array_size, uint32_t max_arrays);
    ~UniqueStoreSmallStringBufferType() override;
    void destroyElements(void *, size_t) override;
    void fallbackCopy(void *newBuffer, const void *oldBuffer, size_t numElems) override;
    void cleanHold(void *buffer, size_t offset, size_t numElems, CleanContext) override;
};

/*
 * Buffer type for external strings in unique store.
 */
class UniqueStoreExternalStringBufferType : public BufferType<UniqueStoreEntry<std::string>> {
public:
    UniqueStoreExternalStringBufferType(uint32_t array_size, uint32_t max_arrays);
    ~UniqueStoreExternalStringBufferType() override;
    void cleanHold(void *buffer, size_t offset, size_t numElems, CleanContext cleanCtx) override;
};

/**
 * Allocator for unique NUL-terminated strings that is accessed via a
 * 32-bit EntryRef.
 */
template <typename RefT = EntryRefT<22> >
class UniqueStoreStringAllocator : public ICompactable
{
public:
    using DataStoreType = DataStoreT<RefT>;
    using EntryType = const char *;
    using WrappedExternalEntryType = UniqueStoreEntry<std::string>;
    using RefType = RefT;
private:
    DataStoreType _store;
    std::vector<std::unique_ptr<BufferTypeBase>> _type_handlers;

    static uint32_t get_type_id(const char *value);

public:
    UniqueStoreStringAllocator();
    ~UniqueStoreStringAllocator() override;
    EntryRef allocate(const char *value);
    void hold(EntryRef ref);
    EntryRef move(EntryRef ref) override;
    const UniqueStoreEntryBase& getWrapped(EntryRef ref) const
    {
        RefType iRef(ref);
        auto &state = _store.getBufferState(iRef.bufferId());
        auto type_id = state.getTypeId();
        if (type_id != 0) {
            return *reinterpret_cast<const UniqueStoreEntryBase *>(_store.template getEntryArray<char>(iRef, state.getArraySize()));
        } else {
            return *_store.template getEntry<WrappedExternalEntryType>(iRef);
        }
    }
    const char *get(EntryRef ref) const
    {
        RefType iRef(ref);
        auto &state = _store.getBufferState(iRef.bufferId());
        auto type_id = state.getTypeId();
        if (type_id != 0) {
            return reinterpret_cast<const UniqueStoreSmallStringEntry *>(_store.template getEntryArray<char>(iRef, state.getArraySize()))->value();
        } else {
            return _store.template getEntry<WrappedExternalEntryType>(iRef)->value().c_str();
        }
    }
    DataStoreType& getDataStore() { return _store; }
    const DataStoreType& getDataStore() const { return _store; }
};

}
