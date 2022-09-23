// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "osmem.h"
#include <vespamalloc/malloc/common.h>
#include <cstdio>
#include <cctype>
#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <sys/statfs.h>
#include <sys/mman.h>
#include <linux/mman.h>
#include <functional>

namespace vespamalloc {

Memory::Memory(size_t blockSize)
    : _blockSize(std::max(blockSize, size_t(getpagesize()))),
      _start(nullptr),
      _end(nullptr)
{ }
Memory::~Memory() = default;

void *
MmapMemory::reserve(size_t & len)
{
    len = 0;
    const size_t wLen(0x1000);
    void * wanted = get(wLen);
    int test = munmap(wanted, wLen);
    ASSERT_STACKTRACE( test == 0 );
    (void) test;
    setStart(wanted);
    setEnd(getStart());
    return nullptr;
}

size_t
findInMemInfo(const char * wanted)
{
    size_t value(0);
    char memInfo[8192];
    int fd(open("/proc/meminfo", O_RDONLY));
    ASSERT_STACKTRACE(fd >= 0);
    if (fd >= 0) {
        int sz(read(fd, memInfo, sizeof(memInfo)));
        ASSERT_STACKTRACE((sz < int(sizeof(memInfo))) && (sz >= 0));
        memInfo[sz] = '\0';
        const char  * found(strstr(memInfo, wanted));
        if (found != nullptr) {
            found += strlen(wanted);
            value = strtoul(found, nullptr, 0);
        }
        close(fd);
    }
    return value;
}

const char *
getToken(const char * & s, const char * e)
{
    for (; (s < e) && isspace(s[0]); s++) { }
    const char * c = s;
    for (; (s < e) && ! isspace(s[0]); s++) { }
    return c;
}

bool
verifyHugePagesMount(const char * mount)
{
    const unsigned int HUGETLBFS_MAGIC(0x958458f6);
    struct statfs64 st;
    int ret(statfs64(mount, &st));
    return (ret == 0) && (st.f_type == HUGETLBFS_MAGIC);
}

MmapMemory::MmapMemory(size_t blockSize) :
    Memory(blockSize),
    _useMAdvLimit(getBlockAlignment()*32),
    _hugePagesFd(-1),
    _hugePagesOffset(0),
    _hugePageSize(0)
{
    setupFAdvise();
    setupHugePages();
}

void
MmapMemory::setupFAdvise()
{
    const char * madv = getenv("VESPA_MALLOC_MADVISE_LIMIT");
    if (madv) {
        _useMAdvLimit = strtoul(madv, nullptr, 0);
    }
}

void
MmapMemory::setupHugePages()
{
    _hugePagesFileName[0] = '\0';
    const char * vespaHugePages = getenv("VESPA_MALLOC_HUGEPAGES");
    if (vespaHugePages && strcmp(vespaHugePages , "no")) {
        int pid(getpid());
        _hugePageSize = findInMemInfo("Hugepagesize:");
        size_t pagesTotal = findInMemInfo("HugePages_Total:");
        if ((_hugePageSize > 0) && (pagesTotal > 0)) {
            if (verifyHugePagesMount(vespaHugePages)) {
                snprintf(_hugePagesFileName, sizeof(_hugePagesFileName), "%s/%d.mem", vespaHugePages, pid);
            } else {
                int fd(open("/proc/mounts", O_RDONLY));
                if (fd >= 0) {
                    char mounts[8192];
                    int sz(read(fd, mounts, sizeof(mounts)));
                    ASSERT_STACKTRACE((sz < int(sizeof(mounts))) && (sz >= 0));
                    (void) sz;
                    const char * c = mounts;
                    while (*c) {
                        const char *e = c;
                        for (; e[0] && (e[0] != '\n'); e++) { }
                        const char *dev = getToken(c, e);
                        (void) dev;
                        const char *mount = getToken(c, e);
                        size_t mountLen(c - mount);
                        const char *fstype = getToken(c, e);
                        if (strstr(fstype, "hugetlbfs") == fstype) {
                            char mountCopy[512];
                            ASSERT_STACKTRACE(mountLen < sizeof(mountCopy));
                            strncpy(mountCopy, mount, mountLen);
                            mountCopy[mountLen] = '\0';
                            if (verifyHugePagesMount(mountCopy)) {
                                snprintf(_hugePagesFileName, sizeof(_hugePagesFileName), "%s/%d.mem", mountCopy, pid);
                                break;
                            }
                        }
                        c = e[0] ? e + 1 : e;
                    }
                    close(fd);
                }
            }
            if (_hugePagesFileName[0] != '\0') {
                _blockSize = std::max(_blockSize, _hugePageSize);
                _hugePagesFd = open(_hugePagesFileName, O_CREAT | O_RDWR, 0755);
                ASSERT_STACKTRACE(_hugePagesFd >= 0);
                int retval(unlink(_hugePagesFileName));
                ASSERT_STACKTRACE(retval == 0);
                (void) retval;
            }
        }
    }
}

MmapMemory::~MmapMemory()
{
    if (_hugePagesFd >= 0) {
        close(_hugePagesFd);
        _hugePagesOffset = 0;
    }
}

void *
MmapMemory::get(size_t len)
{
    void * memory(nullptr);
    int prevErrno = errno;
    memory = getHugePages(len);
    if (memory == nullptr) {
        errno = prevErrno; // The temporary error should not impact if the end is good.
        memory = getNormalPages(len);
    }
    return memory;
}

void *
MmapMemory::getHugePages(size_t len)
{
    void * memory(nullptr);
    if ( ((len & 0x1fffff) == 0) && len) {
        int prevErrno = errno;
        memory = getBasePages(len, MAP_ANON | MAP_PRIVATE | MAP_HUGETLB, -1, 0);
        if (memory == nullptr) {
            if (_hugePagesFd >= 0) {
                errno = prevErrno; // The temporary error should not impact if the end is good.
                memory = getBasePages(len, MAP_SHARED, _hugePagesFd, _hugePagesOffset);
                if (memory) {
                    _hugePagesOffset += len;
                }
            }
        }
    }
    return memory;
}

void *
MmapMemory::getNormalPages(size_t len)
{
    return getBasePages(len, MAP_ANON | MAP_PRIVATE, -1, 0);
}

void *
MmapMemory::getBasePages(size_t len, int mmapOpt, int fd, size_t offset)
{
    char * wanted = reinterpret_cast<char *>(std::max(reinterpret_cast<size_t>(getEnd()), getMinPreferredStartAddress()));
    void * mem(nullptr);
    for (bool ok(false) ; !ok && (mem != MAP_FAILED); wanted += getBlockAlignment()) {
        if (mem != nullptr) {
            int tmp(munmap(mem, len));
            ASSERT_STACKTRACE(tmp == 0);
            (void) tmp;
        }
        // no alignment to _blockSize needed?
        // both 0x10000000000ul*4 and 0x200000 are multiples of the current block size.
        mem = mmap(wanted, len, PROT_READ | PROT_WRITE, mmapOpt, fd, offset);
        ok = (mem == wanted);
    }
    if (mem != MAP_FAILED) {
        if (madvise(mem, len, MADV_HUGEPAGE) != 0) {
            // Just an advise, not everyone will listen...
        }
        if (getStart() == nullptr) {
            setStart(mem);
            // assumes len parameter is always multiple of the current block size.
            setEnd(static_cast<char *>(mem)+len);
        } else if (getEnd() < static_cast<char *>(mem)+len) {
            setEnd(static_cast<char *>(mem)+len);
        }
        return mem;
    }
    return nullptr;
}

bool
MmapMemory::release(void * mem, size_t len)
{
    int ret(0);
    if (_useMAdvLimit <= len) {
        ret = madvise(mem, len, MADV_DONTNEED);
        if (ret != 0) {
            char tmp[256];
            fprintf(stderr, "madvise(%p, %0lx, MADV_DONTNEED) = %d errno=%s\n", mem, len, ret, strerror_r(errno, tmp, sizeof(tmp)));
        }
    }
    return true;
}

bool
MmapMemory::freeTail(void * mem, size_t len)
{
    int ret(0);
    if ((_useMAdvLimit <= len) && (static_cast<char *>(mem) + len) == getEnd()) {
        ret = munmap(mem, len);
        ASSERT_STACKTRACE(ret == 0);
        setEnd(mem);
    }
    return (ret == 0);
}

bool
MmapMemory::reclaim(void * mem, size_t len)
{
    int ret(0);
    if (_useMAdvLimit <= len) {
        ret = madvise(mem, len, MADV_NORMAL);
        if (ret != 0) {
            char tmp[256];
            fprintf(stderr, "madvise(%p, %0lx, MADV_NORMAL) = %d errno=%s\n", mem, len, ret, strerror_r(errno, tmp, sizeof(tmp)));
        }
    }
    return true;
}

}
