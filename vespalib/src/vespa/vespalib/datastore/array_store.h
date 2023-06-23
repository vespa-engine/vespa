// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store_simple_type_mapper.h"
#include "array_store_config.h"
#include "buffer_type.h"
#include "bufferstate.h"
#include "compaction_spec.h"
#include "datastore.h"
#include "entryref.h"
#include "atomic_entry_ref.h"
#include "i_compaction_context.h"
#include "i_compactable.h"
#include "large_array_buffer_type.h"
#include "small_array_buffer_type.h"
#include <vespa/vespalib/util/array.h>
#include <type_traits>

namespace vespalib::datastore {

/**
 * Datastore for storing arrays of type ElemT that is accessed via a 32-bit
 * EntryRef.
 *
 * The default EntryRef type uses 19 bits for offset (524288 values) and 13
 * bits for buffer id (8192 buffers).
 *
 * Buffer type ids [1,max_type_id] are used to allocate small
 * arrays in datastore buffers.
 *
 * The simple type mapper (ArrayStoreSimpleTypeMapper) uses a 1-to-1
 * mapping between type id and array size.
 *
 * If the type mapper has defined a DynamicBufferType type
 * (e.g. ArrayStoreDynamicTypeMapper) then the last part of the buffer type
 * ids range might be for dynamic buffers where maximum array size can
 * grow exponentially as buffer type id increases.
 *
 * Buffer type id 0 is used to heap allocate large arrays as
 * vespalib::Array instances.
 *
 * The max value of max_type_id is (2^(bufferBits - 3) - 1).
 */
template <typename ElemT, typename RefT = EntryRefT<19>, typename TypeMapperT = ArrayStoreSimpleTypeMapper<ElemT> >
class ArrayStore : public ICompactable
{
public:
    using AllocSpec = ArrayStoreConfig::AllocSpec;
    using ArrayRef = vespalib::ArrayRef<ElemT>;
    using ConstArrayRef = vespalib::ConstArrayRef<ElemT>;
    using DataStoreType  = DataStoreT<RefT>;
    using ElemType = ElemT;
    using LargeArray = vespalib::Array<ElemT>;
    using LargeBufferType = typename TypeMapperT::LargeBufferType;
    using RefType = RefT;
    using SmallBufferType = typename TypeMapperT::SmallBufferType;
    using TypeMapper = TypeMapperT;
    struct no_vector { };

    template <class, class = void>
    struct check_dynamic_buffer_type_member {
        static constexpr bool value = false;
        using vector_type = no_vector;
    };

    template <class T>
    struct check_dynamic_buffer_type_member<T, std::void_t<typename T::DynamicBufferType>> {
        static constexpr bool value = true;
        using vector_type = std::vector<typename T::DynamicBufferType>;
    };

    static constexpr bool has_dynamic_buffer_type = check_dynamic_buffer_type_member<TypeMapper>::value;
    using DynamicBufferTypeVector = typename check_dynamic_buffer_type_member<TypeMapper>::vector_type;
private:
    uint32_t                     _largeArrayTypeId;
    uint32_t                     _max_type_id;
    size_t                       _maxSmallArraySize;
    DataStoreType                _store;
    TypeMapper                   _mapper;
    std::vector<SmallBufferType> _smallArrayTypes;
    [[no_unique_address]] DynamicBufferTypeVector _dynamicArrayTypes;
    LargeBufferType              _largeArrayType;
    CompactionSpec               _compaction_spec;
    using generation_t = vespalib::GenerationHandler::generation_t;

    BufferTypeBase* initArrayType(const ArrayStoreConfig &cfg, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, uint32_t type_id);
    void initArrayTypes(const ArrayStoreConfig &cfg, std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    EntryRef addSmallArray(ConstArrayRef array, uint32_t type_id);
    EntryRef allocate_small_array(uint32_t type_id);
    template <typename BufferType>
    EntryRef add_dynamic_array(ConstArrayRef array, uint32_t type_id);
    template <typename BufferType>
    EntryRef  allocate_dynamic_array(size_t array_size, uint32_t type_id);
    EntryRef addLargeArray(ConstArrayRef array);
    EntryRef allocate_large_array(size_t array_size);
    ConstArrayRef getSmallArray(RefT ref, size_t arraySize) const {
        const ElemT *buf = _store.template getEntryArray<ElemT>(ref, arraySize);
        return ConstArrayRef(buf, arraySize);
    }
    template <typename BufferType>
    ConstArrayRef get_dynamic_array(const void* buffer, size_t offset, uint32_t entry_size) const {
        auto entry = BufferType::get_entry(buffer, offset, entry_size);
        auto size = BufferType::get_dynamic_array_size(entry);
        return ConstArrayRef(entry, size);
    }
    ConstArrayRef getLargeArray(RefT ref) const {
        const LargeArray *buf = _store.template getEntry<LargeArray>(ref);
        return ConstArrayRef(&(*buf)[0], buf->size());
    }

public:
    ArrayStore(const ArrayStoreConfig &cfg, std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    ArrayStore(const ArrayStoreConfig &cfg, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, TypeMapper&& mapper);
    ~ArrayStore() override;
    EntryRef add(ConstArrayRef array);
    ConstArrayRef get(EntryRef ref) const {
        if (!ref.valid()) [[unlikely]] {
            return ConstArrayRef();
        }
        RefT internalRef(ref);
        const BufferAndMeta & bufferAndMeta = _store.getBufferMeta(internalRef.bufferId());
        if (bufferAndMeta.getTypeId() != _largeArrayTypeId) [[likely]] {
            if constexpr (has_dynamic_buffer_type) {
                if (_mapper.is_dynamic_buffer(bufferAndMeta.getTypeId())) {
                        return get_dynamic_array<typename TypeMapper::DynamicBufferType>(bufferAndMeta.get_buffer_acquire(), internalRef.offset(), bufferAndMeta.get_entry_size());
                 }
            }
            return getSmallArray(internalRef, bufferAndMeta.get_array_size());
        } else {
            return getLargeArray(internalRef);
        }
    }

    /**
     * Allocate an array of the given size without any instantiation of ElemT elements.
     *
     * Use get_writable() to get a reference to the array for writing.
     *
     * NOTE: In most cases add() should be used instead.
     *       This function is however relevant when serializing objects into char buffers
     *       when e.g. using an ArrayStore<char> for memory management.
     */
    EntryRef allocate(size_t array_size);

    /**
     * Returns a writeable reference to the given array.
     *
     * NOTE: Use with care if reader threads are accessing arrays at the same time.
     *       If so, replacing an element in the array should be an atomic operation.
     */
    ArrayRef get_writable(EntryRef ref) {
        return vespalib::unconstify(get(ref));
    }

    void remove(EntryRef ref);
    EntryRef move_on_compact(EntryRef ref) override;
    ICompactionContext::UP compact_worst(const CompactionStrategy& compaction_strategy);
    // Use this if references to array store is not an array of AtomicEntryRef
    std::unique_ptr<CompactingBuffers> start_compact_worst_buffers(const CompactionStrategy &compaction_strategy);

    vespalib::MemoryUsage getMemoryUsage() const;
    vespalib::MemoryUsage update_stat(const CompactionStrategy& compaction_strategy);
    bool consider_compact() const noexcept { return _compaction_spec.compact() && !_store.has_held_buffers(); }

    // Set compaction spec. Only used by unit tests.
    void set_compaction_spec(CompactionSpec compaction_spec) noexcept { _compaction_spec = compaction_spec; }

    /**
     * Returns the address space usage by this store as the ratio between active buffers
     * and the total number available buffers.
     */
    vespalib::AddressSpace addressSpaceUsage() const;

    // Pass on hold list management to underlying store
    void assign_generation(generation_t current_gen) { _store.assign_generation(current_gen); }
    void reclaim_memory(generation_t oldest_used_gen) { _store.reclaim_memory(oldest_used_gen); }
    vespalib::GenerationHolder &getGenerationHolder() { return _store.getGenerationHolder(); }
    void setInitializing(bool initializing) { _store.setInitializing(initializing); }

    // need object location before construction
    static vespalib::GenerationHolder &getGenerationHolderLocation(ArrayStore &self) {
        return DataStoreBase::getGenerationHolderLocation(self._store);
    }
    // need object location before construction
    static DataStoreBase& get_data_store_base(ArrayStore &self) { return self._store; }

    // Should only be used for unit testing
    const BufferState &bufferState(EntryRef ref);

    bool has_free_lists_enabled() const { return _store.has_free_lists_enabled(); }
    bool has_held_buffers() const noexcept { return _store.has_held_buffers(); }

    const TypeMapper& get_mapper() const noexcept { return _mapper; }

    static ArrayStoreConfig optimizedConfigForHugePage(uint32_t max_type_id,
                                                       size_t hugePageSize,
                                                       size_t smallPageSize,
                                                       size_t max_buffer_size,
                                                       size_t min_num_entries_for_new_buffer,
                                                       float allocGrowFactor);

    static ArrayStoreConfig optimizedConfigForHugePage(uint32_t max_type_id,
                                                       const TypeMapper& mapper,
                                                       size_t hugePageSize,
                                                       size_t smallPageSize,
                                                       size_t max_buffer_size,
                                                       size_t min_num_entries_for_new_buffer,
                                                       float allocGrowFactor);
};

extern template class BufferType<vespalib::Array<uint8_t>>;
extern template class BufferType<vespalib::Array<uint32_t>>;
extern template class BufferType<vespalib::Array<int32_t>>;
extern template class BufferType<vespalib::Array<std::string>>;
extern template class BufferType<vespalib::Array<AtomicEntryRef>>;

}
