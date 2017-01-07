// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "allocchunk.h"

namespace vespamalloc {


void AFListBase::linkInList(AtomicHeadPtr & head, AFListBase * list)
{
    AFListBase * tail;
    for (tail = list; tail->_next != NULL ;tail = tail->_next) { }
    linkIn(head, list, tail);
}

void AFListBase::linkIn(AtomicHeadPtr & head, AFListBase * csl, AFListBase * tail)
{
    HeadPtr oldHead = head.load(std::memory_order_relaxed);
    HeadPtr newHead(csl, oldHead._tag + 1);
    tail->_next = static_cast<AFListBase *>(oldHead._ptr);
    while ( ! head.compare_exchange_weak(oldHead, newHead, std::memory_order_release, std::memory_order_relaxed) ) {
        newHead._tag =  oldHead._tag + 1;
        tail->_next = static_cast<AFListBase *>(oldHead._ptr);
    }
}

AFListBase * AFListBase::linkOut(AtomicHeadPtr & head)
{
    HeadPtr oldHead = head.load(std::memory_order_relaxed);
    AFListBase *csl = static_cast<AFListBase *>(oldHead._ptr);
    if (csl == NULL) {
        return NULL;
    }
    HeadPtr newHead(csl->_next, oldHead._tag + 1);
    while ( ! head.compare_exchange_weak(oldHead, newHead, std::memory_order_acquire, std::memory_order_relaxed) ) {
        csl = static_cast<AFListBase *>(oldHead._ptr);
        if (csl == NULL) {
            return NULL;
        }
        newHead._ptr = csl->_next;
        newHead._tag = oldHead._tag + 1;
    }
    csl->_next = NULL;
    return csl;
}

}
