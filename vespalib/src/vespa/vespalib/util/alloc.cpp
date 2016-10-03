// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "alloc.h"
#include <stdlib.h>
#include <errno.h>
#include <sys/mman.h>
#include <linux/mman.h>
#include <stdexcept>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/log/log.h>
#include <map>
#include <atomic>

LOG_SETUP(".vespalib.alloc");

namespace vespalib {

namespace {

volatile bool _G_hasHugePageFailureJustHappened(false);
bool _G_SilenceCoreOnOOM(false);
int  _G_HugeFlags = 0;
size_t _G_MMapLogLimit = std::numeric_limits<size_t>::max();
size_t _G_MMapNoCoreLimit = std::numeric_limits<size_t>::max();
Lock _G_lock;
std::atomic<size_t> _G_mmapCount(0);

struct MMapInfo {
    MMapInfo() :
        _id(0ul),
        _sz(0ul),
        _stackTrace()
    {}
    MMapInfo(size_t id, size_t sz, const string & stackTrace) :
        _id(id),
        _sz(sz),
        _stackTrace(stackTrace)
    {}
    size_t _id;
    size_t _sz;
    string _stackTrace;
};
typedef std::map<const void *, MMapInfo> MMapStore;
MMapStore _G_HugeMappings;

size_t
readOptionalEnvironmentVar(const char * name, size_t defaultValue) {
    const char * str = getenv(name);
    if (str != nullptr) {
        char * e(nullptr);
        size_t value = strtoul(str, &e, 0);
        if ((e == 0) || (e[0] == '\0')) {
            return value;
        }
        LOG(warning, "Not able to to decode %s='%s' as number. Failed at '%s'", name, str, e);
    }
    return defaultValue;
}

void initializeEnvironment()
{
    _G_HugeFlags = (getenv("VESPA_USE_HUGEPAGES") != nullptr) ? MAP_HUGETLB : 0;
    _G_SilenceCoreOnOOM = (getenv("VESPA_SILENCE_CORE_ON_OOM") != nullptr) ? true : false;
    _G_MMapLogLimit = readOptionalEnvironmentVar("VESPA_MMAP_LOG_LIMIT", std::numeric_limits<size_t>::max());
    _G_MMapNoCoreLimit = readOptionalEnvironmentVar("VESPA_MMAP_NOCORE_LIMIT", std::numeric_limits<size_t>::max());
}

class Initialize {
public:
    Initialize() { initializeEnvironment(); }
};

Initialize _G_initializer;

size_t sum(const MMapStore & s)
{
    size_t sum(0);
    for (auto & p : s) {
        sum += p.second._sz;
    }
    return sum;
}

alloc::HeapAllocator _G_heapAllocatorDefault;
alloc::AlignedHeapAllocator _G_4KalignedHeapAllocator(4096);
alloc::AlignedHeapAllocator _G_512BalignedHeapAllocator(512);
alloc::MMapAllocator _G_mmapAllocatorDefault;
alloc::AutoAllocator _G_autoAllocatorDefault(alloc::MMapAllocator::HUGEPAGE_SIZE, 0);
alloc::AutoAllocator _G_2PautoAllocatorDefault(2 * alloc::MMapAllocator::HUGEPAGE_SIZE, 0);
alloc::AutoAllocator _G_4PautoAllocatorDefault(4 * alloc::MMapAllocator::HUGEPAGE_SIZE, 0);
alloc::AutoAllocator _G_8PautoAllocatorDefault(8 * alloc::MMapAllocator::HUGEPAGE_SIZE, 0);
alloc::AutoAllocator _G_16PautoAllocatorDefault(16 * alloc::MMapAllocator::HUGEPAGE_SIZE, 0);

}

namespace alloc {

MemoryAllocator & HeapAllocator::getDefault() {
    return _G_heapAllocatorDefault;
}

MemoryAllocator & AlignedHeapAllocator::get4K() {
    return _G_4KalignedHeapAllocator;
}

MemoryAllocator & AlignedHeapAllocator::get512B() {
    return _G_512BalignedHeapAllocator;
}

MemoryAllocator & MMapAllocator::getDefault() {
    return _G_mmapAllocatorDefault;
}

MemoryAllocator & AutoAllocator::getDefault() {
    return _G_autoAllocatorDefault;
}

MemoryAllocator & AutoAllocator::get2P() {
    return _G_2PautoAllocatorDefault;
}

MemoryAllocator & AutoAllocator::get4P() {
    return _G_4PautoAllocatorDefault;
}

MemoryAllocator & AutoAllocator::get8P() {
    return _G_8PautoAllocatorDefault;
}

MemoryAllocator & AutoAllocator::get16P() {
    return _G_16PautoAllocatorDefault;
}

void * HeapAllocator::alloc(size_t sz) const {
    return salloc(sz);
}

void * HeapAllocator::salloc(size_t sz) {
    return (sz > 0) ? malloc(sz) : 0;
}

void HeapAllocator::free(void * p, size_t sz) const {
    sfree(p, sz);
}

void HeapAllocator::sfree(void * p, size_t sz) {
    (void) sz; if (p) { ::free(p); }
}

void * AlignedHeapAllocator::alloc(size_t sz) const {
    if (!sz) { return 0; }
    void* ptr;
    int result = posix_memalign(&ptr, _alignment, sz);
    if (result != 0) {
        throw IllegalArgumentException(make_string("posix_memalign(%zu, %zu) failed with code %d", sz, _alignment, result));
    }
    return ptr;
}

void * MMapAllocator::alloc(size_t sz) const {
    return salloc(sz);
}

void * MMapAllocator::salloc(size_t sz)
{
    void * buf(nullptr);
    if (sz > 0) {
        const int flags(MAP_ANON | MAP_PRIVATE);
        const int prot(PROT_READ | PROT_WRITE);
        size_t mmapId = std::atomic_fetch_add(&_G_mmapCount, 1ul);
        string stackTrace;
        if (sz >= _G_MMapLogLimit) {
            stackTrace = getStackTrace(1);
            LOG(info, "mmap %ld of size %ld from %s", mmapId, sz, stackTrace.c_str());
        }
        buf = mmap(nullptr, sz, prot, flags | _G_HugeFlags, -1, 0);
        if (buf == MAP_FAILED) {
            if ( ! _G_hasHugePageFailureJustHappened ) {
                _G_hasHugePageFailureJustHappened = true;
                LOG(debug, "Failed allocating %ld bytes with hugepages due too '%s'."
                          " Will resort to ordinary mmap until it works again.",
                           sz, FastOS_FileInterface::getLastErrorString().c_str());
            }
            buf = mmap(nullptr, sz, prot, flags, -1, 0);
            if (buf == MAP_FAILED) {
                stackTrace = getStackTrace(1);
                string msg = make_string("Failed mmaping anonymous of size %ld errno(%d) from %s", sz, errno, stackTrace.c_str());
                if (_G_SilenceCoreOnOOM) {
                    OOMException oom(msg);
                    oom.setPayload(std::make_unique<SilenceUncaughtException>(oom));
                    throw oom;
                } else {
                    throw OOMException(msg);
                }
            }
        } else {
            if (_G_hasHugePageFailureJustHappened) {
                _G_hasHugePageFailureJustHappened = false;
            }
        }
        if (sz >= _G_MMapNoCoreLimit) {
            if (madvise(buf, sz, MADV_DONTDUMP) != 0) {
                LOG(warning, "Failed madvise(%p, %ld, MADV_DONTDUMP) = '%s'", buf, sz, FastOS_FileInterface::getLastErrorString().c_str());
            }
        }
        if (sz >= _G_MMapLogLimit) {
            LockGuard guard(_G_lock);
            _G_HugeMappings[buf] = MMapInfo(mmapId, sz, stackTrace);
            LOG(info, "%ld mappings of accumulated size %ld", _G_HugeMappings.size(), sum(_G_HugeMappings));
        }
    }
    return buf;
}

void MMapAllocator::free(void * buf, size_t sz) const {
    sfree(buf, sz);
}

void MMapAllocator::sfree(void * buf, size_t sz)
{
    if (buf != nullptr) {
        madvise(buf, sz, MADV_DONTNEED);
        munmap(buf, sz);
        if (sz >= _G_MMapLogLimit) {
            LockGuard guard(_G_lock);
            MMapInfo info = _G_HugeMappings[buf];
            _G_HugeMappings.erase(buf);
            LOG(info, "munmap %ld of size %ld", info._id, info._sz);
            LOG(info, "%ld mappings of accumulated size %ld", _G_HugeMappings.size(), sum(_G_HugeMappings));
        }
    }
}

void * AutoAllocator::alloc(size_t sz) const {
    if (useMMap(sz)) {
        sz = roundUpToHugePages(sz);
        return MMapAllocator::salloc(sz);
    } else {
        if (_alignment == 0) {
            return HeapAllocator::salloc(sz);
        } else {
            return AlignedHeapAllocator(_alignment).alloc(sz);
        }
    }
}

void AutoAllocator::free(void *p, size_t sz) const {
    if (useMMap(sz)) {
        return MMapAllocator::sfree(p, sz);
    } else {
        return HeapAllocator::sfree(p, sz);
    }
}

Alloc
HeapAllocFactory::create(size_t sz)
{
    return Alloc(&HeapAllocator::getDefault(), sz);
}

Alloc
AlignedHeapAllocFactory::create(size_t sz, size_t alignment)
{
    if (alignment == 0) {
        return Alloc(&AlignedHeapAllocator::getDefault(), sz);
    } else if (alignment == 0x200) {
        return Alloc(&AlignedHeapAllocator::get512B(), sz);
    } else if (alignment == 0x1000) {
        return Alloc(&AlignedHeapAllocator::get4K(), sz);
    } else {
        abort();
    }
}

Alloc
MMapAllocFactory::create(size_t sz)
{
    return Alloc(&MMapAllocator::getDefault(), sz);
}

Alloc
AutoAllocFactory::create(size_t sz, size_t mmapLimit, size_t alignment)
{
    if (alignment == 0) {
        if (mmapLimit <= MMapAllocator::HUGEPAGE_SIZE) {
            return Alloc(&AutoAllocator::getDefault(), sz);
        } else if (mmapLimit <= 2*MMapAllocator::HUGEPAGE_SIZE) {
            return Alloc(&AutoAllocator::get2P(), sz);
        } else if (mmapLimit <= 4*MMapAllocator::HUGEPAGE_SIZE) {
            return Alloc(&AutoAllocator::get4P(), sz);
        } else if (mmapLimit <= 8*MMapAllocator::HUGEPAGE_SIZE) {
            return Alloc(&AutoAllocator::get8P(), sz);
        } else {
            return Alloc(&AutoAllocator::get16P(), sz);
        }
    } else {
        abort();
    }
}

}

}
