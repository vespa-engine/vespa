// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include <algorithm>

namespace vespamalloc {

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
struct TaggedPtrT {
    TaggedPtrT() noexcept : _ptr(nullptr), _tag(0) { }
    TaggedPtrT(void *h, size_t t) noexcept : _ptr(h), _tag(t) {}

    void *_ptr;
    size_t _tag;
};

#if defined(__x86_64__)
struct AtomicTaggedPtr {
    AtomicTaggedPtr() noexcept : _ptr(nullptr), _tag(0) { }
    AtomicTaggedPtr(void *h, size_t t) noexcept : _ptr(h), _tag(t) {}

    AtomicTaggedPtr load(std::memory_order = std::memory_order_seq_cst) {
        // Note that this is NOT an atomic load. The current use as the initial load
        // in a compare_exchange loop is safe as a teared load will just give a retry.
        return *this;
    }
    void store(AtomicTaggedPtr ptr) {
        // Note that this is NOT an atomic store. The current use is in a unit test as an initial
        // store before any threads are started. Just done so to keep api compatible with std::atomic as
        // that is the preferred implementation..
        *this = ptr;
    }
    bool
    compare_exchange_weak(AtomicTaggedPtr & oldPtr, AtomicTaggedPtr newPtr, std::memory_order, std::memory_order) {
        char result;
        __asm__ volatile (
        "lock ;"
        "cmpxchg16b %6;"
        "setz %1;"
        : "+m" (*this),
          "=q" (result),
          "+a" (oldPtr._ptr),
          "+d" (oldPtr._tag)
        : "b" (newPtr._ptr),
          "c" (newPtr._tag)
        : "cc", "memory"
        );
        return result;
    }

    void *_ptr;
    size_t _tag;
} __attribute__ ((aligned (16)));

using TaggedPtr = AtomicTaggedPtr;

#else
    using TaggedPtr = TaggedPtrT;
    using AtomicTaggedPtr = std::atomic<TaggedPtr>;
#endif


class AFListBase
{
public:
    using HeadPtr = TaggedPtr;
    using AtomicHeadPtr = std::atomic<TaggedPtr>;

    AFListBase() : _next(nullptr) { }
    void setNext(AFListBase * csl)           { _next = csl; }
    static void init();
    static void linkInList(AtomicHeadPtr & head, AFListBase * list);
    static void linkIn(AtomicHeadPtr & head, AFListBase * csl, AFListBase * tail);
protected:
    AFListBase * getNext()                      { return _next; }
    static AFListBase * linkOut(AtomicHeadPtr & head);
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
    static AFList * linkOut(AtomicHeadPtr & head) {
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
