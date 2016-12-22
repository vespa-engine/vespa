// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include <algorithm>

namespace vespamalloc {

#define ATOMIC_TAGGEDPTR_ALIGNMENT __attribute__ ((aligned (16)))

/**
 * Copied from vespalib to avoid code dependencies.
 */
class Atomic {
public:
    /**
     * @brief Pointer and tag - use instead of bare pointer for cmpSwap()
     *
     * When making a lock-free data structure by using cmpSwap
     * on pointers, you'll often run into the "ABA problem", see
     * http://en.wikipedia.org/wiki/ABA_problem for details.
     * The TaggedPtr makes it easy to do the woraround with tag bits,
     * but requires the double-word compare-and-swap instruction.
     * Very early Amd K7/8 CPUs are lacking this and will fail (Illegal Instruction).
     **/
    struct TaggedPtr {
        TaggedPtr() noexcept : _ptr(nullptr), _tag(0) { }
        TaggedPtr(void *h, size_t t) noexcept : _ptr(h), _tag(t) {}

        void *_ptr;
        size_t _tag;
    };

    static bool cmpSwap(volatile TaggedPtr *dest, TaggedPtr newVal, TaggedPtr oldVal) {
        char result;
        void *ptr;
        size_t tag;
#if defined(__x86_64__)
        __asm__ volatile ("lock ;"
                          "cmpxchg16b %8;"
                          "setz %1;"
                          : "=m" (*dest),
                            "=q" (result),
                            "=a" (ptr),
                            "=d" (tag)
                          : "a" (oldVal._ptr),
                            "d" (oldVal._tag),
                            "b" (newVal._ptr),
                            "c" (newVal._tag),
                            "m" (*dest)
                          : "memory");
#else
#error "Only supports X86_64"
#endif
        return result;
    }
};

class AFListBase
{
public:
    using HeadPtr = Atomic::TaggedPtr;
    AFListBase() : _next(NULL) { }
    void setNext(AFListBase * csl)           { _next = csl; }
    static void init();
    static void linkInList(HeadPtr & head, AFListBase * list);
    static void linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail);
protected:
    AFListBase * getNext()                      { return _next; }
    static AFListBase * linkOut(HeadPtr & head);
private:
    AFListBase       *_next;
};

template <typename MemBlockPtrT>
class AFList : public AFListBase
{
public:
    typedef size_t CountT;
    enum { NumBlocks = 126 };
    AFList() : _count(0) { }
    CountT count()              const { return _count; }
    void add(MemBlockPtrT & ptr)      {
        ptr.free();
        PARANOID_CHECK2( if (full()) { *(int*)0=0; });
        _memBlockList[_count++] = ptr;
    }
    void sub(MemBlockPtrT & mem)      {
        if (empty()) {
            return;
        }
        mem = _memBlockList[--_count];
    }
    bool empty()                const { return (_count == 0); }
    bool full()                 const { return (_count == NumBlocks); }
    size_t fill(void * mem, SizeClassT sc, size_t blocksPerChunk = NumBlocks);
    AFList * getNext()                { return static_cast<AFList *>(AFListBase::getNext()); }
    static AFList * linkOut(HeadPtr & head) {
        return static_cast<AFList *>(AFListBase::linkOut(head));
    }
private:
    CountT        _count;
    MemBlockPtrT _memBlockList[NumBlocks];
};


template <typename MemBlockPtrT>
size_t AFList<MemBlockPtrT>::fill(void * mem, SizeClassT sc, size_t blocksPerChunk)
{
    size_t sz = MemBlockPtrT::classSize(sc);
    int retval(std::max(0, int(blocksPerChunk-_count)));
    char * first = (char *) mem;
    for(int i=0; i < retval; i++) {
        _memBlockList[_count] = MemBlockPtrT(first + i*sz, MemBlockPtrT::unAdjustSize(sz));
        _memBlockList[_count].free();
        _count++;
    }
    return retval;
}

}
