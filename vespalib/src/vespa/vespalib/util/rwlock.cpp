// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rwlock.h"
#include <cassert>

namespace vespalib {

void RWLock::lockRead() {
    MonitorGuard guard(_monitor);
    CounterGuard waitCnt(_waitingReaders);
    while (_givenLocks == -1 || _waitingWriters > 0) {
        guard.wait();
    }
    ++_givenLocks;
}

void RWLock::unlockRead() {
    MonitorGuard guard(_monitor);
    assert(_givenLocks > 0);
    if (--_givenLocks == 0 && _waitingWriters > 0) {
        guard.broadcast();
    }
}

void RWLock::lockWrite() {
    MonitorGuard guard(_monitor);
    CounterGuard waitCnt(_waitingWriters);
    while (_givenLocks != 0) {
        guard.wait();
    }
    _givenLocks = -1;
}

void RWLock::unlockWrite() {
    MonitorGuard guard(_monitor);
    assert(_givenLocks == -1);
    _givenLocks = 0;
    if (_waitingReaders > 0 || _waitingWriters > 0) {
        guard.broadcast();
    }
}

RWLock *
RWLockReader::stealLock() {
    RWLock * ret(_lock);
    assert(ret != nullptr);
    _lock = nullptr;
    return ret;
}

RWLock *
RWLockWriter::stealLock() {
    RWLock * ret(_lock);
    assert(ret != nullptr);
    _lock = nullptr;
    return ret;
}

} // namespace vespalib
