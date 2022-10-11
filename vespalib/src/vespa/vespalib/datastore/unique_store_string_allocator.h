// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastore.h"
#include "entryref.h"
#include "unique_store_add_result.h"
#include "unique_store_entry.h"
#include "i_compactable.h"
#include <cassert>
#include <string>

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

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
    std::shared_ptr<vespalib::alloc::MemoryAllocator> _memory_allocator;
public:
    UniqueStoreSmallStringBufferType(uint32_t array_size, uint32_t max_arrays, std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator);
    ~UniqueStoreSmallStringBufferType() override;
    void destroyElements(void *, ElemCount) override;
    void fallbackCopy(void *newBuffer, const void *oldBuffer, ElemCount numElems) override;
    void cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext) override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
};

/*
 * Buffer type for external strings in unique store.
 */
class UniqueStoreExternalStringBufferType : public BufferType<UniqueStoreEntry<std::string>> {
    std::shared_ptr<vespalib::alloc::MemoryAllocator> _memory_allocator;
public:
    UniqueStoreExternalStringBufferType(uint32_t array_size, uint32_t max_arrays, std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator);
    ~UniqueStoreExternalStringBufferType() override;
    void cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx) override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
};

/**
 * Allocator for unique NUL-terminated strings that is accessed via a
 * 32-bit EntryRef. Multiple buffer types are used. Small strings use
 * a common buffer type handler with different parameters for array
 * size (which denotes number of bytes set aside for meta data
 * (reference count), string and NUL byte. Large strings use a
 * different buffer type handler where buffer contains meta data
 * (reference count) and an std::string, while the string value is on
 * the heap.  string_allocator::get_type_id() is used to map from
 * string length to type id.
 */
template <typename RefT = EntryRefT<22> >
class UniqueStoreStringAllocator : public ICompactable
{
public:
    using DataStoreType = DataStoreT<RefT>;
    using EntryType = const char *;
    using EntryConstRefType = const char *;
    using WrappedExternalEntryType = UniqueStoreEntry<std::string>;
    using RefType = RefT;
private:
    DataStoreType _store;
    std::vector<std::unique_ptr<BufferTypeBase>> _type_handlers;

    static uint32_t get_type_id(const char *value);

public:
    UniqueStoreStringAllocator(std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    ~UniqueStoreStringAllocator() override;
    EntryRef allocate(const char *value);
    void hold(EntryRef ref);
    EntryRef move_on_compact(EntryRef ref) override;
    const UniqueStoreEntryBase& get_wrapped(EntryRef ref) const {
        RefType iRef(ref);
        auto &state = _store.getBufferState(iRef.bufferId());
        auto type_id = state.getTypeId();
        if (type_id != 0) {
            return *reinterpret_cast<const UniqueStoreEntryBase *>(_store.template getEntryArray<char>(iRef, state.getArraySize()));
        } else {
            return *_store.template getEntry<WrappedExternalEntryType>(iRef);
        }
    }
    const char *get(EntryRef ref) const {
        RefType iRef(ref);
        auto &state = _store.getBufferState(iRef.bufferId());
        auto type_id = state.getTypeId();
        if (type_id != 0) {
            return reinterpret_cast<const UniqueStoreSmallStringEntry *>(_store.template getEntryArray<char>(iRef, state.getArraySize()))->value();
        } else {
            return _store.template getEntry<WrappedExternalEntryType>(iRef)->value().c_str();
        }
    }
    DataStoreType& get_data_store() { return _store; }
    const DataStoreType& get_data_store() const { return _store; }
};

extern template class BufferType<UniqueStoreEntry<std::string> >;

}
