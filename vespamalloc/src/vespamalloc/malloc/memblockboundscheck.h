// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/common.h>
#include <vespamalloc/util/callstack.h>

namespace vespamalloc {

class MemBlockBoundsCheckBaseTBase : public CommonT<5>
{
public:
    typedef StackEntry<StackReturnEntry> Stack;
    void * rawPtr()          { return _ptr; }
    void *ptr()              { unsigned *p((unsigned*)_ptr); return p ? (p+4) : NULL; }
    const void *ptr()  const { unsigned *p((unsigned*)_ptr); return p ? (p+4) : NULL; }

    void setThreadId(int th)              { if (_ptr) { static_cast<uint32_t*>(_ptr)[2] = th; } }
    bool allocated()                const { return (static_cast<unsigned*>(_ptr)[3] == ALLOC_MAGIC); }
    size_t size()                   const { return static_cast<const uint64_t *>(_ptr)[0]; }
    int threadId()                  const { return static_cast<int*>(_ptr)[2]; }
    Stack * callStack()                   { return reinterpret_cast<Stack *>((char *)_ptr + size() + 4*sizeof(unsigned)); }
    const Stack * callStack()       const { return reinterpret_cast<const Stack *>((const char *)_ptr + size() + 4*sizeof(unsigned)); }
    void fillMemory(size_t sz) {
        if (_fillValue != NO_FILL) {
            memset(ptr(), _fillValue, sz);
        }
    }
    static void bigBlockLimit(size_t lim) { _bigBlockLimit = lim; }
    static void dumpFile(FILE * fp)       { _logFile = fp; }
    static void setFill(uint8_t pattern)  { _fillValue = pattern; }
    static bool verifySizeClass(int sc)   { return sc >= 0; }

    template<typename T>
    void readjustAlignment(const T & segment) {
        size_t ptr_class_size = this->classSize(T::adjustedClassSize(segment.sizeClass(_ptr)));
        size_t clamped_class_size = std::min(size_t(0x10000), ptr_class_size);
        size_t bitmask = ~(clamped_class_size - 1);
        size_t tmp = reinterpret_cast<size_t>(_ptr);
        tmp &= bitmask;
        _ptr = reinterpret_cast<void *>(tmp);
    }
    void logBigBlock(size_t exact, size_t adjusted, size_t gross) const __attribute__((noinline));
protected:
    MemBlockBoundsCheckBaseTBase(void * p) : _ptr(p) { }
    void verifyFill() const __attribute__((noinline));

    void setSize(size_t sz) { static_cast<uint64_t *>(_ptr)[0] = sz; }

    enum {
        ALLOC_MAGIC = 0xF1E2D3C4,
        FREE_MAGIC = 0x63242367,
        HEAD_MAGIC3 = 0x5BF29BC7,
        TAIL_MAGIC = 0x1A2B3C4D
    };
    enum { NO_FILL = 0xa8};

    void * _ptr;

    static FILE *_logFile;
    static size_t _bigBlockLimit;
    static uint8_t _fillValue;
};

template <size_t MaxSizeClassMultiAllocC, size_t StackTraceLen>
class MemBlockBoundsCheckBaseT : public MemBlockBoundsCheckBaseTBase
{
public:
    enum {
        MaxSizeClassMultiAlloc = MaxSizeClassMultiAllocC,
        SizeClassSpan = (MaxSizeClassMultiAllocC-5)
    };
    MemBlockBoundsCheckBaseT() : MemBlockBoundsCheckBaseTBase(NULL) { }
    MemBlockBoundsCheckBaseT(void * p) : MemBlockBoundsCheckBaseTBase(p ? (unsigned *)p-4 : NULL) { }
    MemBlockBoundsCheckBaseT(void * p, size_t sz) : MemBlockBoundsCheckBaseTBase(p) { setSize(sz); }
    MemBlockBoundsCheckBaseT(void * p, size_t, bool) : MemBlockBoundsCheckBaseTBase(p) { }
    bool validCommon() const {
        const unsigned *p(reinterpret_cast<const unsigned*>(_ptr));
        return p
            && ((p[3] == ALLOC_MAGIC) || (p[3] == FREE_MAGIC))
            && *(reinterpret_cast<const unsigned *> ((const char*)_ptr + size() + 4*sizeof(unsigned) + StackTraceLen*sizeof(void *))) == TAIL_MAGIC;
    }
    bool validAlloc1() const {
        unsigned *p((unsigned*)_ptr);
        return validCommon() && (p[3] == ALLOC_MAGIC);
    }
    bool validFree1()  const {
        unsigned *p((unsigned*)_ptr);
        if (_fillValue != NO_FILL) {
            verifyFill();
        }
        return validCommon() && (p[3] == FREE_MAGIC);
    }
    void alloc(bool log) {
        unsigned *p((unsigned*)_ptr);
        if (p) {
            p[3] = ALLOC_MAGIC;
            if (StackTraceLen) {
                Stack * cStack = callStack();
                if (log) {
                    Stack::fillStack(cStack, StackTraceLen);
                } else {
                    cStack[0] = Stack();
                }
            }
        }
    }

    void free() __attribute__((noinline)) {
        static_cast<unsigned*>(_ptr)[3] = FREE_MAGIC;
        fillMemory(size());
        setTailMagic();
    }
    void setExact(size_t sz)              { init(sz); }
    size_t callStackLen()           const {
        const Stack * stack = callStack();
        // Use int to avoid compiler warning about always true.
        for (int i(0); i < (int)StackTraceLen; i++) {
            if (! stack[i].valid()) {
                return i+1;
            }
        }
        return StackTraceLen;
    }
    static size_t adjustSize(size_t sz)   { return sz + ((4+1)*sizeof(unsigned) + StackTraceLen*sizeof(void *)); }
    static size_t unAdjustSize(size_t sz) { return sz - ((4+1)*sizeof(unsigned) + StackTraceLen*sizeof(void *)); }
    static void dumpInfo(size_t level) __attribute__((noinline));
    static size_t getMinSizeForAlignment(size_t align, size_t sz) { return sz + align; }
    void info(FILE * os, unsigned level=0) const __attribute__((noinline));

protected:
    void setTailMagic() { *(reinterpret_cast<unsigned *> ((char*)_ptr + size() + 4*sizeof(unsigned) + StackTraceLen*sizeof(void *))) = TAIL_MAGIC; }
    void init(size_t sz) {
        if (_ptr) {
            setSize(sz);
            setTailMagic();
        }
    }
};

} // namespace vespamalloc

