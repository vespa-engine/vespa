// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadlocals.h"

namespace storage {

namespace memfile {

vespalib::Lock ThreadStatic::_threadLock;
uint16_t ThreadStatic::_nextThreadIdx = 0;
__thread int ThreadStatic::_threadIdx = -1;

void ThreadStatic::initThreadIndex()
{
    if (_threadIdx == -1) {
        vespalib::LockGuard guard(_threadLock);
        _threadIdx = _nextThreadIdx;
        ++_nextThreadIdx;
    }
}

}

}
