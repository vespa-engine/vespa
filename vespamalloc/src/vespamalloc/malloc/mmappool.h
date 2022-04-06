// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <mutex>
#include <unordered_map>

namespace vespamalloc {

class MMapPool {
public:
    MMapPool();
    MMapPool(const MMapPool &) = delete;
    MMapPool & operator =(const MMapPool &) = delete;
    ~MMapPool();
    void * mmap(size_t sz);
    void unmap(void *);
    size_t get_size(void *) const;
    size_t getNumMappings() const;
    size_t getMmappedBytes() const;
private:
    struct MMapInfo {
        MMapInfo(size_t id, size_t sz) : _id(id), _sz(sz) { }
        size_t _id;
        size_t _sz;
    };
    const size_t        _page_size;
    const int           _huge_flags;
    std::atomic<size_t> _count;
    std::atomic<bool>   _has_hugepage_failure_just_happened;
    mutable std::mutex  _mutex;
    std::unordered_map<const void *, MMapInfo> _mappings;
};

}
