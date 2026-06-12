// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/transient_memory_tracker.h>

#include <mutex>
#include <span>
#include <vector>

namespace vespalib {

/**
 * This is a backing store intended for small size variable length data elemets.
 * It has the important property that once an object has been allocated it does not move in memory.
 * It will start of by allocating one backing buffer and items stored will be appended here.
 * When limit is exceeded a new buffer is allocated with twice the size of the previous and so it goes.
 * You can also provide an optional lock to make it thread safe.
 **/
class MemoryDataStore {
public:
    explicit MemoryDataStore(alloc::Alloc&& initialAlloc);
    MemoryDataStore(const MemoryDataStore&) = delete;
    MemoryDataStore(MemoryDataStore&&) noexcept = delete;
    ~MemoryDataStore();
    MemoryDataStore& operator=(const MemoryDataStore&) = delete;
    MemoryDataStore& operator=(MemoryDataStore&&) noexcept = delete;

    [[nodiscard]] std::span<std::byte> alloc(size_t size);
    /**
     * Will allocate space and copy the data in. The returned pointer will be valid
     * for the lifetime of this object.
     * @return A pointer/reference to the freshly stored object.
     */
    [[nodiscard]] std::span<const std::byte> push_back(std::span<const std::byte> data);
    void clear() noexcept;

private:
    std::vector<alloc::Alloc> _buffers;
    size_t                    _writePos;
    std::mutex                _lock;
    TransientMemoryTracker    _tracker;
    size_t                    _transient_memory;
};

} // namespace vespalib
