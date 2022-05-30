// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mmappool.h"
#include "common.h"
#include <sys/mman.h>
#include <algorithm>
#include <unistd.h>

namespace vespamalloc {

MMapPool::MMapPool()
    : _page_size(getpagesize()),
      _huge_flags((getenv("VESPA_USE_HUGEPAGES") != nullptr) ? MAP_HUGETLB : 0),
      _count(0),
      _mutex(),
      _mappings()
{

}

MMapPool::~MMapPool() {
    ASSERT_STACKTRACE(_mappings.empty());
}

size_t
MMapPool::getNumMappings() const {
    std::lock_guard guard(_mutex);
    return _mappings.size();
}

size_t
MMapPool::getMmappedBytes() const {
    std::lock_guard guard(_mutex);
    size_t sum(0);
    std::for_each(_mappings.begin(), _mappings.end(), [&sum](const auto & e){ sum += e.second._sz; });
    return sum;
}

void *
MMapPool::mmap(size_t sz) {
    void * buf(nullptr);
    ASSERT_STACKTRACE((sz & (_page_size - 1)) == 0);
    if (sz > 0) {
        const int flags(MAP_ANON | MAP_PRIVATE);
        const int prot(PROT_READ | PROT_WRITE);
        size_t mmapId = _count.fetch_add(1);
        if (sz >= _G_bigBlockLimit) {
            fprintf(_G_logFile, "mmap %ld of size %ld from : ", mmapId, sz);
            logStackTrace();
        }
        buf = ::mmap(nullptr, sz, prot, flags | _huge_flags, -1, 0);
        if (buf == MAP_FAILED) {
            if (!_has_hugepage_failure_just_happened) {
                _has_hugepage_failure_just_happened = true;
            }
            buf = ::mmap(nullptr, sz, prot, flags, -1, 0);
            if (buf == MAP_FAILED) {
                fprintf(_G_logFile, "Failed mmaping anonymous of size %ld errno(%d) from : ", sz, errno);
                logStackTrace();
                abort();
            }
        } else {
            if (_has_hugepage_failure_just_happened) {
                _has_hugepage_failure_just_happened = false;
            }
        }
#ifdef __linux__
        if (madvise(buf, sz, MADV_HUGEPAGE) != 0) {
            // Just an advise, not everyone will listen...
        }
        if (sz >= _G_bigBlockLimit) {
            if (madvise(buf, sz, MADV_DONTDUMP) != 0) {
                std::error_code ec(errno, std::system_category());
                fprintf(_G_logFile, "Failed madvise(%p, %ld, MADV_DONTDUMP) = '%s'\n", buf, sz,
                    ec.message().c_str());
            }
        }
#endif
        std::lock_guard guard(_mutex);
        auto [it, inserted] = _mappings.insert(std::make_pair(buf, MMapInfo(mmapId, sz)));
        ASSERT_STACKTRACE(inserted);
        if (sz >= _G_bigBlockLimit) {
            size_t sum(0);
            std::for_each(_mappings.begin(), _mappings.end(), [&sum](const auto & e){ sum += e.second._sz; });
            fprintf(_G_logFile, "%ld mappings of accumulated size %ld\n", _mappings.size(), sum);
        }
    }
    return buf;
}

void
MMapPool::unmap(void * ptr) {
    size_t sz;
    {
        std::lock_guard guard(_mutex);
        auto found = _mappings.find(ptr);
        if (found == _mappings.end()) {
            fprintf(_G_logFile, "Not able to unmap %p as it is not registered: ", ptr);
            logStackTrace();
            abort();
        }
        sz = found->second._sz;
        _mappings.erase(found);
    }
    int munmap_ok = ::munmap(ptr, sz);
    ASSERT_STACKTRACE(munmap_ok == 0);
}

size_t
MMapPool::get_size(void * ptr) const {
    std::lock_guard guard(_mutex);
    auto found = _mappings.find(ptr);
    ASSERT_STACKTRACE(found != _mappings.end());
    return found->second._sz;
}

}
