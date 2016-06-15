// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>

#include <boost/noncopyable.hpp>
#include <boost/thread/locks.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition_variable.hpp>


namespace vespalib {


/* Blocks a writer from being called while readers are active and vice versa.
 * This is only intended to be used by a single writer.
 */
class BlockWriterMutex : boost::noncopyable {
    typedef boost::mutex ReadersMutex;

    ReadersMutex _readersMutex;
    int _readers;

    boost::condition_variable _noReaders;

    void lockImpl(int sign) {
        boost::unique_lock<ReadersMutex> readersLock(_readersMutex);
        while ((sign*_readers) > 0)
            _noReaders.wait(readersLock);
        _readers -= sign;
    }

    void unlockImpl(int sign) {
        ReadersMutex::scoped_lock readersLock(_readersMutex);
        _readers += sign;
        if (_readers == 0)
            _noReaders.notify_all();
    }

public:
    typedef boost::shared_lock<BlockWriterMutex> ReaderLock;
    typedef boost::unique_lock<BlockWriterMutex> WriterLock;

    BlockWriterMutex()
        :_readers(0)
    {}

    void lock() {
        lockImpl(1);
    }

    void unlock() {
        unlockImpl(1);
    }

    void lock_shared() {
        lockImpl(-1);
    }

    void unlock_shared() {
        unlockImpl(-1);
    }
};

} //namespace vespalib


