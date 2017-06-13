// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/rwlock.h>

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

} // namespace vespalib
