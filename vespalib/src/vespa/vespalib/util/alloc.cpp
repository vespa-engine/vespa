// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "alloc.h"
#include <sys/mman.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/sync.h>
#include <map>
#include <atomic>
#include <unordered_map>
#include <vespa/fastos/file.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.alloc");

namespace vespalib {

namespace {

volatile bool _G_hasHugePageFailureJustHappened(false);
bool _G_SilenceCoreOnOOM(false);
int  _G_HugeFlags = 0;
const size_t _G_pageSize = getpagesize();
size_t _G_MMapLogLimit = std::numeric_limits<size_t>::max();
size_t _G_MMapNoCoreLimit = std::numeric_limits<size_t>::max();
Lock _G_lock;
std::atomic<size_t> _G_mmapCount(0);

size_t
roundUp2PageSize(size_t sz) {
    return (sz + (_G_pageSize - 1)) & ~(_G_pageSize - 1);
}

struct MMapInfo {
    MMapInfo() :
        _id(0ul),
        _sz(0ul),
        _stackTrace()
    { }
    MMapInfo(size_t id, size_t sz, const string & stackTrace) :
        _id(id),
        _sz(sz),
        _stackTrace(stackTrace)
    { }
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

class MMapLimitAndAlignment {
public:
    MMapLimitAndAlignment(size_t mmapLimit, size_t alignment);
    uint32_t hash() const { return _key; }
    bool operator == (MMapLimitAndAlignment rhs) const { return _key == rhs._key; }
private:
    uint32_t _key;
};

void verifyMMapLimitAndAlignment(size_t mmapLimit, size_t alignment) __attribute__((noinline));

void verifyMMapLimitAndAlignment(size_t mmapLimit, size_t alignment) {
    if ((0x01ul << Optimized::msbIdx(mmapLimit)) != mmapLimit) {
        throw IllegalArgumentException(make_string("We only support mmaplimit(%0lx) to be a power of 2", mmapLimit));
    }
    if ((alignment != 0) && (0x01ul << Optimized::msbIdx(alignment)) != alignment) {
        throw IllegalArgumentException(make_string("We only support alignment(%0lx) to be a power of 2", alignment));
    }
}

MMapLimitAndAlignment::MMapLimitAndAlignment(size_t mmapLimit, size_t alignment) :
    _key(Optimized::msbIdx(mmapLimit) | Optimized::msbIdx(alignment) << 6)
{
    verifyMMapLimitAndAlignment(mmapLimit, alignment);
}
}

namespace alloc {

class HeapAllocator : public MemoryAllocator {
public:
    PtrAndSize alloc(size_t sz) const override;
    void free(PtrAndSize alloc) const override;
    size_t resize_inplace(PtrAndSize, size_t) const override { return 0; }
    static PtrAndSize salloc(size_t sz);
    static void sfree(PtrAndSize alloc);
    static MemoryAllocator & getDefault();
};

class AlignedHeapAllocator : public HeapAllocator {
public:
    AlignedHeapAllocator(size_t alignment) : _alignment(alignment) { }
    PtrAndSize alloc(size_t sz) const override;
    static MemoryAllocator & get4K();
    static MemoryAllocator & get1K();
    static MemoryAllocator & get512B();
private:
    size_t _alignment;
};

class MMapAllocator : public MemoryAllocator {
public:
    PtrAndSize alloc(size_t sz) const override;
    void free(PtrAndSize alloc) const override;
    size_t resize_inplace(PtrAndSize current, size_t newSize) const override;
    static size_t sresize_inplace(PtrAndSize current, size_t newSize);
    static PtrAndSize salloc(size_t sz, void * wantedAddress);
    static void sfree(PtrAndSize alloc);
    static MemoryAllocator & getDefault();
private:
    static size_t extend_inplace(PtrAndSize current, size_t newSize);
    static size_t shrink_inplace(PtrAndSize current, size_t newSize);
};

class AutoAllocator : public MemoryAllocator {
public:
    AutoAllocator(size_t mmapLimit, size_t alignment) : _mmapLimit(mmapLimit), _alignment(alignment) { }
    PtrAndSize alloc(size_t sz) const override;
    void free(PtrAndSize alloc) const override;
    size_t resize_inplace(PtrAndSize current, size_t newSize) const override;
    static MemoryAllocator & getDefault();
    static MemoryAllocator & getAllocator(size_t mmapLimit, size_t alignment);
private:
    size_t roundUpToHugePages(size_t sz) const {
        return (_mmapLimit >= MemoryAllocator::HUGEPAGE_SIZE)
            ? MMapAllocator::roundUpToHugePages(sz)
            : sz;
    }
    bool isMMapped(size_t sz) const { return (sz >= _mmapLimit); }
    bool useMMap(size_t sz) const {
        if (_mmapLimit >= HUGEPAGE_SIZE) {
            return (sz + (HUGEPAGE_SIZE >> 1) - 1) >= _mmapLimit;
        } else {
            return (sz >= _mmapLimit);
        }
    }
    size_t _mmapLimit;
    size_t _alignment;
};


namespace {

struct MMapLimitAndAlignmentHash {
    std::size_t operator ()(MMapLimitAndAlignment key) const { return key.hash(); }
};

using AutoAllocatorsMap = std::unordered_map<MMapLimitAndAlignment, AutoAllocator::UP, MMapLimitAndAlignmentHash>;
using AutoAllocatorsMapWithDefault = std::pair<AutoAllocatorsMap, alloc::MemoryAllocator *>;

void createAlignedAutoAllocators(AutoAllocatorsMap & map, size_t mmapLimit) {
    for (size_t alignment : {0,0x200, 0x400, 0x1000}) {
        MMapLimitAndAlignment key(mmapLimit, alignment);
        auto result = map.emplace(key, AutoAllocator::UP(new AutoAllocator(mmapLimit, alignment)));
        (void) result;
        assert( result.second );

    }
}

AutoAllocatorsMap
createAutoAllocators() {
    AutoAllocatorsMap map;
    map.reserve(3*5);
    for (size_t pages : {1,2,4,8,16}) {
        size_t mmapLimit = pages * MemoryAllocator::HUGEPAGE_SIZE;
        createAlignedAutoAllocators(map, mmapLimit);
    }
    return map;
}

MemoryAllocator &
getAutoAllocator(AutoAllocatorsMap & map, size_t mmapLimit, size_t alignment) {
    MMapLimitAndAlignment key(mmapLimit, alignment);
    auto found = map.find(key);
    if (found == map.end()) {
        throw IllegalArgumentException(make_string("We currently have no support for mmapLimit(%0lx) and alignment(%0lx)", mmapLimit, alignment));
    }
    return *(found->second);
}

MemoryAllocator &
getDefaultAutoAllocator(AutoAllocatorsMap & map) {
    return getAutoAllocator(map, 1 * MemoryAllocator::HUGEPAGE_SIZE, 0);
}

AutoAllocatorsMapWithDefault
createAutoAllocatorsWithDefault() {
    AutoAllocatorsMapWithDefault tmp(createAutoAllocators(), nullptr);
    tmp.second = &getDefaultAutoAllocator(tmp.first);
    return tmp;
}


AutoAllocatorsMapWithDefault  _G_availableAutoAllocators = createAutoAllocatorsWithDefault();
alloc::HeapAllocator _G_heapAllocatorDefault;
alloc::AlignedHeapAllocator _G_4KalignedHeapAllocator(1024);
alloc::AlignedHeapAllocator _G_1KalignedHeapAllocator(4096);
alloc::AlignedHeapAllocator _G_512BalignedHeapAllocator(512);
alloc::MMapAllocator _G_mmapAllocatorDefault;

}

MemoryAllocator &
HeapAllocator::getDefault() {
    return _G_heapAllocatorDefault;
}

MemoryAllocator &
AlignedHeapAllocator::get4K() {
    return _G_4KalignedHeapAllocator;
}

MemoryAllocator & AlignedHeapAllocator::get1K() {
    return _G_1KalignedHeapAllocator;
}

MemoryAllocator &
AlignedHeapAllocator::get512B() {
    return _G_512BalignedHeapAllocator;
}

MemoryAllocator &
MMapAllocator::getDefault() {
    return _G_mmapAllocatorDefault;
}

MemoryAllocator &
AutoAllocator::getDefault() {
    return *_G_availableAutoAllocators.second;
}

MemoryAllocator & AutoAllocator::getAllocator(size_t mmapLimit, size_t alignment) {
    return getAutoAllocator(_G_availableAutoAllocators.first, mmapLimit, alignment);
}

MemoryAllocator::PtrAndSize
HeapAllocator::alloc(size_t sz) const {
    return salloc(sz);
}

MemoryAllocator::PtrAndSize
HeapAllocator::salloc(size_t sz) {
    return PtrAndSize((sz > 0) ? malloc(sz) : nullptr, sz);
}

void HeapAllocator::free(PtrAndSize alloc) const {
    sfree(alloc);
}

void HeapAllocator::sfree(PtrAndSize alloc) {
    if (alloc.first) { ::free(alloc.first); }
}

MemoryAllocator::PtrAndSize
AlignedHeapAllocator::alloc(size_t sz) const {
    if (!sz) { return PtrAndSize(nullptr, 0); }
    void* ptr;
    int result = posix_memalign(&ptr, _alignment, sz);
    if (result != 0) {
        throw IllegalArgumentException(make_string("posix_memalign(%zu, %zu) failed with code %d", sz, _alignment, result));
    }
    return PtrAndSize(ptr, sz);
}

size_t
MMapAllocator::resize_inplace(PtrAndSize current, size_t newSize) const {
    return sresize_inplace(current, newSize);
}

MemoryAllocator::PtrAndSize
MMapAllocator::alloc(size_t sz) const {
    return salloc(sz, nullptr);
}

MemoryAllocator::PtrAndSize
MMapAllocator::salloc(size_t sz, void * wantedAddress)
{
    void * buf(nullptr);
    sz = roundUp2PageSize(sz);
    if (sz > 0) {
        const int flags(MAP_ANON | MAP_PRIVATE);
        const int prot(PROT_READ | PROT_WRITE);
        size_t mmapId = std::atomic_fetch_add(&_G_mmapCount, 1ul);
        string stackTrace;
        if (sz >= _G_MMapLogLimit) {
            stackTrace = getStackTrace(1);
            LOG(info, "mmap %ld of size %ld from %s", mmapId, sz, stackTrace.c_str());
        }
        buf = mmap(wantedAddress, sz, prot, flags | _G_HugeFlags, -1, 0);
        if (buf == MAP_FAILED) {
            if ( ! _G_hasHugePageFailureJustHappened ) {
                _G_hasHugePageFailureJustHappened = true;
                LOG(debug, "Failed allocating %ld bytes with hugepages due too '%s'."
                          " Will resort to ordinary mmap until it works again.",
                           sz, FastOS_FileInterface::getLastErrorString().c_str());
            }
            buf = mmap(wantedAddress, sz, prot, flags, -1, 0);
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
    return PtrAndSize(buf, sz);
}

size_t
MMapAllocator::sresize_inplace(PtrAndSize current, size_t newSize) {
    newSize = roundUp2PageSize(newSize);
    if (newSize > current.second) {
        return extend_inplace(current, newSize);
    } else if (newSize < current.second) {
        return shrink_inplace(current, newSize);
    } else {
        return current.second;
    }
}

size_t
MMapAllocator::extend_inplace(PtrAndSize current, size_t newSize) {
    PtrAndSize got = MMapAllocator::salloc(newSize - current.second, static_cast<char *>(current.first)+current.second);
    if ((static_cast<const char *>(current.first) + current.second) == static_cast<const char *>(got.first)) {
        return current.second + got.second;
    } else {
        MMapAllocator::sfree(got);
        return 0;
    }
}

size_t
MMapAllocator::shrink_inplace(PtrAndSize current, size_t newSize) {
    PtrAndSize toUnmap(static_cast<char *>(current.first)+newSize, current.second - newSize);
    sfree(toUnmap);
    return newSize;
}

void MMapAllocator::free(PtrAndSize alloc) const {
    sfree(alloc);
}

void MMapAllocator::sfree(PtrAndSize alloc)
{
    if (alloc.first != nullptr) {
        int retval = madvise(alloc.first, alloc.second, MADV_DONTNEED);
        assert(retval == 0);
        retval = munmap(alloc.first, alloc.second);
        assert(retval == 0);
        if (alloc.second >= _G_MMapLogLimit) {
            LockGuard guard(_G_lock);
            MMapInfo info = _G_HugeMappings[alloc.first];
            assert(alloc.second == info._sz);
            _G_HugeMappings.erase(alloc.first);
            LOG(info, "munmap %ld of size %ld", info._id, info._sz);
            LOG(info, "%ld mappings of accumulated size %ld", _G_HugeMappings.size(), sum(_G_HugeMappings));
        }
    }
}

size_t
AutoAllocator::resize_inplace(PtrAndSize current, size_t newSize) const {
    if (isMMapped(current.second) && useMMap(newSize)) {
        newSize = roundUpToHugePages(newSize);
        return MMapAllocator::sresize_inplace(current, newSize);
    } else {
        return 0;
    }
}

MMapAllocator::PtrAndSize
AutoAllocator::alloc(size_t sz) const {
    if (useMMap(sz)) {
        sz = roundUpToHugePages(sz);
        return MMapAllocator::salloc(sz, nullptr);
    } else {
        if (_alignment == 0) {
            return HeapAllocator::salloc(sz);
        } else {
            return AlignedHeapAllocator(_alignment).alloc(sz);
        }
    }
}

void
AutoAllocator::free(PtrAndSize alloc) const {
    if (isMMapped(alloc.second)) {
        return MMapAllocator::sfree(alloc);
    } else {
        return HeapAllocator::sfree(alloc);
    }
}

Alloc
Alloc::allocHeap(size_t sz)
{
    return Alloc(&HeapAllocator::getDefault(), sz);
}

bool
Alloc::resize_inplace(size_t newSize)
{
    size_t extendedSize = _allocator->resize_inplace(_alloc, newSize);
    if (extendedSize >= newSize) {
        _alloc.second = extendedSize;
        return true;
    }
    return false;
}

Alloc
Alloc::allocAlignedHeap(size_t sz, size_t alignment)
{
    if (alignment == 0) {
        return Alloc(&AlignedHeapAllocator::getDefault(), sz);
    } else if (alignment == 0x200) {
        return Alloc(&AlignedHeapAllocator::get512B(), sz);
    } else if (alignment == 0x400) {
        return Alloc(&AlignedHeapAllocator::get1K(), sz);
    } else if (alignment == 0x1000) {
        return Alloc(&AlignedHeapAllocator::get4K(), sz);
    } else {
        throw IllegalArgumentException(make_string("Alloc::allocAlignedHeap(%zu, %zu) does not support %zu alignment", sz, alignment, alignment));
    }
}

Alloc
Alloc::allocMMap(size_t sz)
{
    return Alloc(&MMapAllocator::getDefault(), sz);
}

Alloc
Alloc::alloc()
{
    return Alloc(&AutoAllocator::getDefault());
}

Alloc
Alloc::alloc(size_t sz, size_t mmapLimit, size_t alignment)
{
    return Alloc(&AutoAllocator::getAllocator(mmapLimit, alignment), sz);
}

}

}
