// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/util/callstack.h>
#include <vespamalloc/malloc/common.h>
#include <stdio.h>

namespace vespamalloc {

template <size_t MinSizeClassC, size_t MaxSizeClassMultiAllocC>
class MemBlockT : public CommonT<MinSizeClassC>
{
    static const size_t MAX_ALIGN= 0x200000ul;
public:
    typedef StackEntry<StackReturnEntry> Stack;
    enum {
        MaxSizeClassMultiAlloc = MaxSizeClassMultiAllocC,
        SizeClassSpan = (MaxSizeClassMultiAllocC-MinSizeClassC)
    };
    MemBlockT() : _ptr(NULL) { }
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
    void setExact(size_t )    { }
    void alloc(bool )         { }
    void setThreadId(int )    { }
    void free()               { }
    size_t size()       const { return 0; }
    bool allocated()    const { return false; }
    int threadId()      const { return 0; }
    void info(FILE *, unsigned level=0) const  { (void) level; }
    Stack * callStack()                   { return NULL; }
    size_t callStackLen()           const { return 0; }
    void fillMemory(size_t)               { }
    void logBigBlock(size_t exact, size_t adjusted, size_t gross) const __attribute__((noinline));

    static size_t adjustSize(size_t sz)   { return sz; }
    static size_t unAdjustSize(size_t sz) { return sz; }
    static void dumpInfo(size_t level);
    static void dumpFile(FILE * fp)       { _logFile = fp; }
    static void bigBlockLimit(size_t lim) { _bigBlockLimit = lim; }
    static void setFill(uint8_t ) { }
    static bool verifySizeClass(int sc) { (void) sc; return true; }
    static size_t getMinSizeForAlignment(size_t align, size_t sz) {
        return (sz < MAX_ALIGN)
                   ? std::max(sz, align)
                   : (align < MAX_ALIGN) ? sz : sz + align;
    }
private:
    void * _ptr;
    static FILE *_logFile;
    static size_t _bigBlockLimit;
};

typedef MemBlockT<5, 20> MemBlock;

}

