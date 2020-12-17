// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/util/callstack.h>
#include <vespamalloc/malloc/common.h>
#include <stdio.h>

namespace vespamalloc {

template <size_t MinSizeClassC, size_t MaxSizeClassMultiAllocC>
class MemBlockT : public CommonT<MinSizeClassC>
{
public:
    using Parent = CommonT<MinSizeClassC>;
    using Stack = StackEntry<StackReturnEntry>;
    enum {
        MaxSizeClassMultiAlloc = MaxSizeClassMultiAllocC,
        SizeClassSpan = (MaxSizeClassMultiAllocC-MinSizeClassC)
    };
    MemBlockT() : _ptr(nullptr) { }
    MemBlockT(void * p) : _ptr(p) { }
    MemBlockT(void * p, size_t /*sz*/) : _ptr(p) { }
    MemBlockT(void * p, size_t, bool) : _ptr(p) { }
    template<typename T>
    void readjustAlignment(const T & segment)  { (void) segment; }
    void *rawPtr()            { return _ptr; }
    void *ptr()               { return _ptr; }
    const void *ptr()   const { return _ptr; }
    bool validAlloc()   const { return true; }
    bool validFree()    const { return true; }
    void setExact(size_t)     { }
    void setExact(size_t, std::align_val_t )    { }
    void alloc(bool )         { }
    void setThreadId(uint32_t ) { }
    void free()               { }
    size_t size()       const { return 0; }
    bool allocated()    const { return false; }
    uint32_t threadId()      const { return 0; }
    void info(FILE *, unsigned level=0) const  { (void) level; }
    Stack * callStack()                   { return nullptr; }
    size_t callStackLen()           const { return 0; }
    void fillMemory(size_t)               { }
    void logBigBlock(size_t exact, size_t adjusted, size_t gross) const __attribute__((noinline));

    static size_t adjustSize(size_t sz)   { return sz; }
    static size_t adjustSize(size_t sz, std::align_val_t)   { return sz; }
    static size_t unAdjustSize(size_t sz) { return sz; }
    static void dumpInfo(size_t level);
    static void dumpFile(FILE * fp)       { _logFile = fp; }
    static void bigBlockLimit(size_t lim);
    static void setFill(uint8_t ) { }
    static bool verifySizeClass(int sc) { (void) sc; return true; }
    static size_t getMinSizeForAlignment(size_t align, size_t sz) {
        return (sz < Parent::MAX_ALIGN)
                   ? std::max(sz, align)
                   : (align < Parent::MAX_ALIGN) ? sz : sz + align;
    }
private:
    void * _ptr;
    static FILE *_logFile;
    static size_t _bigBlockLimit;
};

typedef MemBlockT<5, 20> MemBlock;
template <> void MemBlock::dumpInfo(size_t level);
extern template class MemBlockT<5, 20>;

}
