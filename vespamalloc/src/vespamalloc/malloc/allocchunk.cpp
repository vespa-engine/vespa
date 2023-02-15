// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "allocchunk.h"

namespace vespamalloc {


void AFListBase::linkInList(AtomicHeadPtr & head, AFListBase * list) noexcept
{
    AFListBase * tail;
    for (tail = list; tail->_next != nullptr ;tail = tail->_next) { }
    linkIn(head, list, tail);
}

void AFListBase::linkIn(AtomicHeadPtr & head, AFListBase * csl, AFListBase * tail) noexcept
{
    HeadPtr oldHead = head.load(std::memory_order_relaxed);
    HeadPtr newHead(csl, oldHead._tag + 1);
    tail->_next = static_cast<AFListBase *>(oldHead._ptr);
    while ( __builtin_expect(! head.compare_exchange_weak(oldHead, newHead, std::memory_order_release, std::memory_order_relaxed), false) ) {
        newHead._tag =  oldHead._tag + 1;
        tail->_next = static_cast<AFListBase *>(oldHead._ptr);
    }
}

AFListBase * AFListBase::linkOut(AtomicHeadPtr & head) noexcept
{
    HeadPtr oldHead = head.load(std::memory_order_relaxed);
    auto *csl = static_cast<AFListBase *>(oldHead._ptr);
    if (csl == nullptr) {
        return nullptr;
    }
    HeadPtr newHead(csl->_next, oldHead._tag + 1);
    while ( __builtin_expect(! head.compare_exchange_weak(oldHead, newHead, std::memory_order_acquire, std::memory_order_relaxed), false) ) {
        csl = static_cast<AFListBase *>(oldHead._ptr);
        if (csl == nullptr) {
            return nullptr;
        }
        newHead._ptr = csl->_next;
        newHead._tag = oldHead._tag + 1;
    }
    csl->_next = nullptr;
    return csl;
}

}
