// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_free_list.h"
#include "buffer_stats.h"
#include "buffer_type.h"
#include "entryref.h"
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/array.h>

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
 * This class also supports use of free lists, where previously allocated elements in the buffer can be re-used.
 * First the element is put on hold, then on the free list (counted as dead) to be re-used.
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
    BufferFreeList _free_list;
    std::atomic<BufferTypeBase*> _typeHandler;
    Alloc           _buffer;
    uint32_t        _arraySize;
    uint16_t        _typeId;
    std::atomic<State> _state;
    bool            _disableElemHoldList : 1;
    bool            _compacting : 1;

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
     * @param bufferId       Id of buffer to be active.
     * @param typeId         Registered data type id for buffer.
     * @param typeHandler    Type handler for registered data type.
     * @param elementsNeeded Number of elements needed to be free in the memory allocated.
     * @param buffer         Start of allocated buffer return value.
     */
    void onActive(uint32_t bufferId, uint32_t typeId, BufferTypeBase *typeHandler,
                  size_t elementsNeeded, std::atomic<void*>& buffer);

    /**
     * Transition from ACTIVE to HOLD state.
     */
    void onHold(uint32_t buffer_id);

    /**
     * Transition from HOLD to FREE state.
     */
    void onFree(std::atomic<void*>& buffer);

    /**
     * Disable hold of elements, just mark elements as dead without cleanup.
     * Typically used when tearing down data structure in a controlled manner.
     */
    void disableElemHoldList();

    /**
     * Update stats to reflect that the given elements are put on hold.
     * Returns true if element hold list is disabled for this buffer.
     */
    bool hold_elems(size_t num_elems, size_t extra_bytes);

    /**
     * Free the given elements and update stats accordingly.
     *
     * The given entry ref is put on the free list (if enabled).
     * Hold cleaning of elements is executed on the buffer type.
     */
    void free_elems(EntryRef ref, size_t num_elems, size_t ref_offset);

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
    uint32_t get_used_arrays() const noexcept { return size() / _arraySize; }
    void fallbackResize(uint32_t bufferId, size_t elementsNeeded, std::atomic<void*>& buffer, Alloc &holdBuffer);

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

}
