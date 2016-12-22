// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "allocchunk.h"

namespace vespamalloc {

char AFListBase::_atomicLinkSpace[sizeof(AFListBase::AtomicLink)];
char AFListBase::_lockedLinkSpace[sizeof(AFListBase::LockedLink)];
AFListBase::LinkI     *AFListBase::_link = NULL;

void AFListBase::init()
{
    _link =  new (_atomicLinkSpace)AtomicLink();
}

AFListBase::LinkI::~LinkI() { }

void AFListBase::linkInList(HeadPtr & head, AFListBase * list)
{
    AFListBase * tail;
    for (tail = list; tail->_next != NULL ;tail = tail->_next) { }
    linkIn(head, list, tail);
}

void AFListBase::AtomicLink::linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail)
{
    HeadPtr oldHead = head;
    HeadPtr newHead(csl, oldHead._tag + 1);
    tail->_next = static_cast<AFListBase *>(oldHead._ptr);
    while ( ! Atomic::cmpSwap(&head, newHead, oldHead) ) {
        oldHead = head;
        newHead._tag =  oldHead._tag + 1;
        tail->_next = static_cast<AFListBase *>(oldHead._ptr);
    }
}

void AFListBase::LockedLink::linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail)
{
    Guard guard(_mutex);
    HeadPtr newHead(csl, head._tag + 1);
    tail->_next = static_cast<AFListBase *>(head._ptr);
    head = newHead;
}

AFListBase * AFListBase::LockedLink::linkOut(HeadPtr & head)
{
    Guard guard(_mutex);
    HeadPtr oldHead = head;
    AFListBase *csl = static_cast<AFListBase *>(oldHead._ptr);
    if (csl == NULL) {
        return NULL;
    }
    HeadPtr newHead(csl->_next, oldHead._tag + 1);
    head = newHead;
    csl->_next = NULL;
    return csl;
}

AFListBase * AFListBase::AtomicLink::linkOut(HeadPtr & head)
{
    HeadPtr oldHead = head;
    AFListBase *csl = static_cast<AFListBase *>(oldHead._ptr);
    if (csl == NULL) {
        return NULL;
    }
    HeadPtr newHead(csl->_next, oldHead._tag + 1);
    while ( ! Atomic::cmpSwap(&head, newHead, oldHead) ) {
        oldHead = head;
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
