// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/guard.h>

class RWLockTest;

namespace vespalib {

/**
 * @brief An RWLock is a reader/writer lock. It can either be held by
 * any number of readers or a single writer at any time.
 *
 * The RWLockReader and RWLockWriter classes are used to acquire and
 * release reader and writer locks respectively.
 *
 * Writer locks have priority above reader locks to prevent
 * starvation.
 **/
class RWLock
{
private:
    friend class ::RWLockTest;
    friend class RWLockReader;
    friend class RWLockWriter;

    int     _givenLocks;
    int     _waitingReaders;
    int     _waitingWriters;
    Monitor _monitor;

    void lockRead();
    void unlockRead();
    void lockWrite();
    void unlockWrite();
public:
    /**
     * @brief Create a new RWLock
     **/
    RWLock()
        : _givenLocks(0),
          _waitingReaders(0),
          _waitingWriters(0),
          _monitor() {}
    /**
     * @brief Create a new RWLock, ignoring the right hand side.
     *
     * It makes no sense to copy the state of an RWLock, but we want
     * to allow copying objects that contain RWLock objects.
     *
     * @param rhs ignore this
     **/
    RWLock(const RWLock &rhs)
        : _givenLocks(0),
          _waitingReaders(0),
          _waitingWriters(0),
          _monitor() { (void) rhs;}
    /**
     * @brief Assignment operator ignoring the right hand side.
     *
     * It makes no sense to assign the state of one RWLock to another,
     * but we want to allow assigning objects that contain RWLock
     * objects.
     *
     * @param rhs ignore this
     **/
    RWLock &operator=(const RWLock &rhs) {
        (void) rhs;
        return *this;
    }

    /**
     * To get an instance of RWLockReader or RWLockWriter that isn't
     * associated with a specific RWLock at initialization, you may
     * construct them from this tag type.
     **/
    struct InitiallyUnlockedGuard {};
};

#ifndef IAM_DOXYGEN
class RWLockReaderHandover
{
private:
    friend class RWLockReader;
    RWLock *_lock;
    RWLockReaderHandover(const RWLockReaderHandover &);
    RWLockReaderHandover &operator=(const RWLockReaderHandover &);
    RWLockReaderHandover(RWLock *m) : _lock(m) {}
public:
};

class RWLockWriterHandover
{
private:
    friend class RWLockWriter;
    RWLock *_lock;
    RWLockWriterHandover(const RWLockWriterHandover &);
    RWLockWriterHandover &operator=(const RWLockWriterHandover &);
    RWLockWriterHandover(RWLock *m) : _lock(m) {}
public:
};
#endif


/**
 * @brief An RWLockReader holds a reader lock on an RWLock.
 *
 * The lock is acquired in the constructor and released in the
 * destructor.
 *
 * RWLockReader has destructive copy (like unique_ptr). Assigning from
 * or copying a RWLockReader has the semantic of transferring the lock
 * from one object to the other.  Note that assigning from or copying
 * a RWLockReader that does not have a lock will result in an assert.
 **/
class RWLockReader
{
private:
    RWLock * _lock;
    RWLock * stealLock();
    void cleanup() { if (_lock != nullptr) { _lock->unlockRead(); } }
public:

    /**
     * @brief Obtain reader lock.
     *
     * This will block until a reader lock can be acquired.
     *
     * @param lock the underlying RWLock object
     **/
    RWLockReader(RWLock &lock) : _lock(&lock) { _lock->lockRead(); }

    /**
     * @brief Construct initially unlocked guard.
     * @param tag (unused) marker argument
     **/
    RWLockReader(const RWLock::InitiallyUnlockedGuard &tag) : _lock(nullptr) { (void)tag; }

    /**
     * @brief Steal the lock from the given RWLockReader
     *
     * @param rhs steal the lock from this one
     **/
    RWLockReader(RWLockReader &rhs) : _lock(rhs.stealLock()) {}

    /**
     * @brief Steal the lock from the given RWLockReader
     *
     * @param rhs steal the lock from this one
     **/
    RWLockReader &operator=(RWLockReader & rhs) {
        if (this != & rhs) {
            cleanup();
            _lock = rhs.stealLock();
        }
        return *this;
    }

    /**
     * @brief Release the lock obtained in the constructor
     **/
    ~RWLockReader() { cleanup(); }

#ifndef IAM_DOXYGEN
    RWLockReader(const RWLockReaderHandover &rhs) : _lock(rhs._lock) {}
    operator RWLockReaderHandover() { return RWLockReaderHandover(stealLock()); }
#endif
};


/**
 * @brief An RWLockWriter holds a writer lock on an RWLock.
 *
 * The lock is acquired in the constructor and released in the
 * destructor.
 *
 * RWLockWriter has destructive copy (like unique_ptr). Assigning from
 * or copying a RWLockWriter has the semantic of transferring the lock
 * from one object to the other, and assignment is similar.  Note that
 * assigning from or copying a RWLockWriter that does not have a lock
 * will result in an assert.
 **/
class RWLockWriter
{
private:
    RWLock * _lock;
    RWLock * stealLock();
    void cleanup() { if (_lock != nullptr) { _lock->unlockWrite(); } }
public:

    /**
     * @brief Obtain writer lock.
     *
     * This will block until a writer lock can be acquired.
     *
     * @param lock the underlying RWLock object
     **/
    RWLockWriter(RWLock &lock) : _lock(&lock) { _lock->lockWrite(); }

    /**
     * @brief Construct initially unlocked guard.
     * @param tag (unused) marker argument
     **/
    RWLockWriter(const RWLock::InitiallyUnlockedGuard &tag) : _lock(nullptr) { (void)tag; }

    /**
     * @brief Steal the lock from the given RWLockWriter
     *
     * @param rhs steal the lock from this one
     **/
    RWLockWriter(RWLockWriter &rhs) : _lock(rhs.stealLock()) {}

    /**
     * @brief Steal the lock from the given RWLockWriter
     *
     * @param rhs steal the lock from this one
     **/
    RWLockWriter &operator=(RWLockWriter & rhs) {
        if (this != & rhs) {
            cleanup();
            _lock = rhs.stealLock();
        }
        return *this;
    }

    /**
     * @brief Release the lock obtained in the constructor
     **/
    ~RWLockWriter() { cleanup(); }

#ifndef IAM_DOXYGEN
    RWLockWriter(const RWLockWriterHandover &rhs) : _lock(rhs._lock) {}
    operator RWLockWriterHandover() { return RWLockWriterHandover(stealLock()); }
#endif
};

} // namespace vespalib

