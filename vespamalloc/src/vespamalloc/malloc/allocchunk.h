// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/common.h>
#include <algorithm>

namespace vespamalloc {

class AFListBase
{
public:
    typedef Atomic::TaggedPtr HeadPtr;
    AFListBase() : _next(NULL) { }
    void setNext(AFListBase * csl)           { _next = csl; }
    static void init();
    static void enableThreadSupport()   { _link->enableThreadSupport(); }
    static void linkInList(HeadPtr & head, AFListBase * list);
    static void linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail) {
        _link->linkIn(head, csl, tail);
    }
protected:
    AFListBase * getNext()                      { return _next; }
    static AFListBase * linkOut(HeadPtr & head) { return _link->linkOut(head); }
private:
    class LinkI
    {
    public:
        virtual ~LinkI();
        virtual void enableThreadSupport() { }
        virtual void linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail) = 0;
        virtual AFListBase * linkOut(HeadPtr & head) = 0;
    };
    class AtomicLink : public LinkI
    {
    private:
        virtual void linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail);
        virtual AFListBase * linkOut(HeadPtr & head);
    };
    class LockedLink : public LinkI
    {
    public:
        virtual void enableThreadSupport() { _mutex.init(); }
    private:
        virtual void linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail);
        virtual AFListBase * linkOut(HeadPtr & head);
        Mutex _mutex;
    };
    static char _atomicLinkSpace[sizeof(AtomicLink)];
    static char _lockedLinkSpace[sizeof(LockedLink)];
    static LinkI     *_link;
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
