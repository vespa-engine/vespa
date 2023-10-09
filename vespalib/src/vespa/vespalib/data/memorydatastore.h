// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/array.h>
#include <vector>
#include <mutex>

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
    class Reference {
    public:
        explicit Reference(void * data_) noexcept : _data(data_) { }
        void * data() noexcept { return _data; }
        const char * c_str() const noexcept { return static_cast<const char *>(_data); }
    private:
        void   * _data;
    };
    MemoryDataStore(alloc::Alloc && initialAlloc, std::mutex * lock);
    MemoryDataStore(const MemoryDataStore &) = delete;
    MemoryDataStore & operator = (const MemoryDataStore &) = delete;
    ~MemoryDataStore();
    /**
     * Will allocate space and copy the data in. The returned pointer will be valid
     * for the lifetime of this object.
     * @return A pointer/reference to the freshly stored object.
     */
    Reference push_back(const void * data, size_t sz);
    void swap(MemoryDataStore & rhs) { _buffers.swap(rhs._buffers); }
    void clear() noexcept {
        _buffers.clear();
    }
private:
    std::vector<alloc::Alloc> _buffers;
    size_t                    _writePos;
    std::mutex              * _lock;
};

} // namespace vespalib

