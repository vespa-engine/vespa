// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "allocchunk.h"

namespace vespamalloc {


void AFListBase::linkInList(HeadPtr & head, AFListBase * list)
{
    AFListBase * tail;
    for (tail = list; tail->_next != NULL ;tail = tail->_next) { }
    linkIn(head, list, tail);
}

void AFListBase::linkIn(HeadPtr & head, AFListBase * csl, AFListBase * tail)
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

AFListBase * AFListBase::linkOut(HeadPtr & head)
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
