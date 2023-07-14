// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_free_list.h"
#include "buffer_stats.h"
#include "buffer_type.h"
#include "entryref.h"
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/alloc.h>

namespace vespalib::datastore {

/**
 * Represents a memory allocated buffer (used in a data store) with its state.
 *
 * This class has no direct knowledge of what kind of data is stored in the buffer.
 * It uses a type handler (BufferTypeBase) to manage allocation and de-allocation of a specific data type.
 *
 * A newly allocated buffer starts in state FREE where no memory is allocated.
 * It then transitions to state ACTIVE via onActive(), where memory is allocated based on calculation from BufferTypeBase.
 * It then transitions to state HOLD via onHold() when the buffer is no longer needed.
 * It is kept in this state until all reader threads are no longer accessing the buffer.
 * Finally, it transitions back to FREE via onFree() and memory is de-allocated.
 *
 * This class also supports use of free lists, where previously allocated entries in the buffer can be re-used.
 * First the entry is put on hold, then on the free list (counted as dead) to be re-used.
 */
class BufferState
{
public:
    using Alloc = vespalib::alloc::Alloc;

    enum class State : uint8_t {
        FREE,
        ACTIVE,
        HOLD
    };

private:
    InternalBufferStats _stats;
    BufferFreeList      _free_list;
    std::atomic<BufferTypeBase*> _typeHandler;
    Alloc              _buffer;
    uint32_t           _arraySize;
    uint16_t           _typeId;
    std::atomic<State> _state;
    bool               _disable_entry_hold_list : 1;
    bool               _compacting : 1;

    static void *get_buffer(Alloc& buffer, uint32_t buffer_underflow_size) noexcept { return static_cast<char *>(buffer.get()) + buffer_underflow_size; }
    void *get_buffer(uint32_t buffer_underflow_size) noexcept { return get_buffer(_buffer, buffer_underflow_size); }
public:
    /**
     * TODO: Check if per-buffer free lists are useful, or if
     * compaction should always be used to free up whole buffers.
     */

    BufferState();
    BufferState(const BufferState &) = delete;
    BufferState & operator=(const BufferState &) = delete;
    ~BufferState();

    /**
     * Transition from FREE to ACTIVE state.
     *
     * @param bufferId            Id of buffer to be active.
     * @param typeId              Registered data type id for buffer.
     * @param typeHandler         Type handler for registered data type.
     * @param free_entries_needed Number of entries needed to be free in the memory allocated.
     * @param buffer              Start of allocated buffer return value.
     */
    void on_active(uint32_t bufferId, uint32_t typeId, BufferTypeBase *typeHandler,
                   size_t free_entries_needed, std::atomic<void*>& buffer);

    /**
     * Transition from ACTIVE to HOLD state.
     */
    void onHold(uint32_t buffer_id);

    /**
     * Transition from HOLD to FREE state.
     */
    void onFree(std::atomic<void*>& buffer);

    /**
     * Disable hold of entries, just mark entries as dead without cleanup.
     * Typically used when tearing down data structure in a controlled manner.
     */
    void disable_entry_hold_list();

    /**
     * Update stats to reflect that the given entries are put on hold.
     * Returns true if entry hold list is disabled for this buffer.
     */
    bool hold_entries(size_t num_entries, size_t extra_bytes);

    /**
     * Free the given entries and update stats accordingly.
     *
     * The given entry ref is put on the free list (if enabled).
     * Hold cleaning of entries is executed on the buffer type.
     */
    void free_entries(EntryRef ref, size_t num_entries, size_t ref_offset);

    BufferStats& stats() { return _stats; }
    const BufferStats& stats() const { return _stats; }

    void enable_free_list(FreeList& type_free_list) { _free_list.enable(type_free_list); }
    void disable_free_list() { _free_list.disable(); }

    size_t size() const { return _stats.size(); }
    size_t capacity() const { return _stats.capacity(); }
    size_t remaining() const { return _stats.remaining(); }
    void dropBuffer(uint32_t buffer_id, std::atomic<void*>& buffer);
    uint32_t getTypeId() const { return _typeId; }
    uint32_t getArraySize() const { return _arraySize; }
    bool getCompacting() const { return _compacting; }
    void setCompacting() { _compacting = true; }
    void fallback_resize(uint32_t bufferId, size_t free_entries_needed, std::atomic<void*>& buffer, Alloc &holdBuffer);

    bool isActive(uint32_t typeId) const {
        return (isActive() && (_typeId == typeId));
    }
    bool isActive() const { return (getState() == State::ACTIVE); }
    bool isOnHold() const { return (getState() == State::HOLD); }
    bool isFree() const { return (getState() == State::FREE); }
    State getState() const { return _state.load(std::memory_order_relaxed); }
    const BufferTypeBase *getTypeHandler() const { return _typeHandler.load(std::memory_order_relaxed); }
    BufferTypeBase *getTypeHandler() { return _typeHandler.load(std::memory_order_relaxed); }

    void resume_primary_buffer(uint32_t buffer_id);
};

class BufferAndMeta {
public:
    BufferAndMeta() : BufferAndMeta(nullptr, nullptr, 0, 0) { }
    std::atomic<void*>& get_atomic_buffer() noexcept { return _buffer; }
    void* get_buffer_relaxed() noexcept { return _buffer.load(std::memory_order_relaxed); }
    const void* get_buffer_acquire() const noexcept { return _buffer.load(std::memory_order_acquire); }
    uint32_t getTypeId() const { return _typeId; }
    uint32_t get_array_size() const { return _array_size; }
    BufferState * get_state_relaxed() { return _state.load(std::memory_order_relaxed); }
    const BufferState * get_state_acquire() const { return _state.load(std::memory_order_acquire); }
    uint32_t get_entry_size() const noexcept { return _entry_size; }
    void setTypeId(uint32_t typeId) { _typeId = typeId; }
    void set_array_size(uint32_t arraySize) { _array_size = arraySize; }
    void set_entry_size(uint32_t entry_size) noexcept { _entry_size = entry_size; }
    void set_state(BufferState * state) { _state.store(state, std::memory_order_release); }
private:
    BufferAndMeta(void* buffer, BufferState * state, uint32_t typeId, uint32_t arraySize)
        : _buffer(buffer),
          _state(state),
          _typeId(typeId),
          _array_size(arraySize)
    { }
    std::atomic<void*>        _buffer;
    std::atomic<BufferState*> _state;
    uint32_t                  _typeId;
    union {
        uint32_t _array_size; // Valid unless buffer type is dynamic array buffer type
        uint32_t _entry_size; // Valid if buffer type is dynamic array buffer type
    };
};

}
