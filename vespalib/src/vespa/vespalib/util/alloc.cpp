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

void * AlignedHeapAlloc::alloc(size_t sz, size_t alignment)
{
    if (!sz) {
        return 0;
    }
    void* ptr;
    int result = posix_memalign(&ptr, alignment, sz);
    if (result != 0) {
        throw IllegalArgumentException(
                make_string("posix_memalign(%zu, %zu) failed with code %d",
                            sz, alignment, result));
    }
    return ptr;
}

namespace {

volatile bool _G_hasHugePageFailureJustHappened(false);
volatile bool _G_SilenceCoreOnOOM(false);
volatile int  _G_HugeFlags = -1;
volatile size_t _G_MMapLogLimit = std::numeric_limits<size_t>::max();
volatile size_t _G_MMapNoCoreLimit = std::numeric_limits<size_t>::max();
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
    if (str != NULL) {
        char * e(NULL);
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
    _G_HugeFlags = (getenv("VESPA_USE_HUGEPAGES") != NULL) ? MAP_HUGETLB : 0;
    _G_SilenceCoreOnOOM = (getenv("VESPA_SILENCE_CORE_ON_OOM") != NULL) ? true : false;
    _G_MMapLogLimit = readOptionalEnvironmentVar("VESPA_MMAP_LOG_LIMIT", std::numeric_limits<size_t>::max());
    _G_MMapNoCoreLimit = readOptionalEnvironmentVar("VESPA_MMAP_NOCORE_LIMIT", std::numeric_limits<size_t>::max());
}

size_t sum(const MMapStore & s)
{
    size_t sum(0);
    for (auto & p : s) {
        sum += p.second._sz;
    }
    return sum;
}

}
void * MMapAlloc::alloc(size_t sz)
{
    void * buf(NULL);
    if (sz > 0) {
        const int flags(MAP_ANON | MAP_PRIVATE);
        const int prot(PROT_READ | PROT_WRITE);
        if (_G_HugeFlags == -1) {
            initializeEnvironment();
        }
        size_t mmapId = std::atomic_fetch_add(&_G_mmapCount, 1ul);
        string stackTrace;
        if (sz >= _G_MMapLogLimit) {
            stackTrace = getStackTrace(1);
            LOG(info, "mmap %ld of size %ld from %s", mmapId, sz, stackTrace.c_str());
        }
        buf = mmap(NULL, sz, prot, flags | _G_HugeFlags, -1, 0);
        if (buf == MAP_FAILED) {
            if ( ! _G_hasHugePageFailureJustHappened ) {
                _G_hasHugePageFailureJustHappened = true;
                LOG(debug, "Failed allocating %ld bytes with hugepages due too '%s'."
                          " Will resort to ordinary mmap until it works again.",
                           sz, FastOS_FileInterface::getLastErrorString().c_str());
            }
            buf = mmap(NULL, sz, prot, flags, -1, 0);
            if (buf == MAP_FAILED) {
                stackTrace = getStackTrace(1);
                string msg = make_string("Failed mmaping anonymous of size %ld errno(%d) from %s", sz, errno, stackTrace.c_str());
                if (_G_SilenceCoreOnOOM) {
                    LOG(fatal, "Will exit with code 66 due to: %s", msg.c_str());
                    exit(66);  //OR _exit() ?
                } else {
                    throw OOMException(msg, VESPA_STRLOC);
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

void MMapAlloc::free(void * buf, size_t sz)
{
    if (buf != NULL) {
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

}
