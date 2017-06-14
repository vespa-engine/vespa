// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2006 Yahoo

#pragma once

#include <vespa/fastos/mutex.h>
#include <vespa/fastos/cond.h>
#include <vespa/fastos/time.h>

namespace vespalib {

#ifndef IAM_DOXYGEN
class LockGuardHandover
{
private:
    friend class LockGuard;
    FastOS_MutexInterface *_mutex;
    LockGuardHandover(const LockGuardHandover &);
    LockGuardHandover &operator=(const LockGuardHandover &);
    LockGuardHandover(FastOS_MutexInterface *m) : _mutex(m) {}
public:
};


class MonitorGuardHandover
{
private:
    friend class MonitorGuard;
    FastOS_CondInterface *_cond;
    MonitorGuardHandover(const MonitorGuardHandover &);
    MonitorGuardHandover &operator=(const MonitorGuardHandover &);
    MonitorGuardHandover(FastOS_CondInterface *c) : _cond(c) {}
public:
};
#endif

/**
 * @brief A Lock is a synchronization primitive used to ensure mutual
 * exclusion.
 *
 * Use a LockGuard to hold a lock inside a scope.
 *
 * It is possible to obtain a lock on a const Lock object.
 *
 * @see TryLock
 **/
class Lock
{
private:
    friend class LockGuard;
    friend class TryLock;

    mutable FastOS_Mutex _mutex;
public:
    /**
     * @brief Create a new Lock.
     *
     * Creates a Lock that has mutex instrumentation disabled.
     **/
    Lock() : _mutex() {}
    /**
     * @brief Create a new Lock.
     *
     * Creates a Lock with support for mutex instrumentation.
     *
     * @param name mutex category (for instrumentation)
     * @param leaf false if you will lock other locks while holding this one,
     *             true if you only lock this as the last (leaf) lock
     *             (for instrumentation).
     **/
    Lock(const char *name, bool leaf) : _mutex() {(void) name; (void) leaf; }
    /**
     * @brief Copy a Lock.
     *
     * Create a new Lock with mutex instrumentation settings obtained
     * from the given lock. No other information is copied.  Normally
     * only used when copy-constructing a bigger object containing a
     * Lock.
     *
     * @param rhs other Lock
     **/
    Lock(const Lock &rhs) : _mutex() { (void) rhs; }
    /**
     * @brief No-op assignment operator.
     *
     * Assignment operator ignoring the right hand side. It makes no
     * sense to assign the state of one Lock to another, but we want
     * to allow assignment of bigger objects that contain Lock
     * objects.
     *
     * @param rhs other Lock (right hand side)
     **/
    Lock &operator=(const Lock &rhs) {
        (void) rhs;
        return *this;
    }
};


/**
 * @brief A Monitor is a synchronization primitive used to protect
 * data access and also facilitate signaling and waiting between
 * threads.
 *
 * A LockGuard can be used to obtain a lock on a Monitor. If you also
 * want to send or wait for signals, you need to use a MonitorGuard.
 *
 * It is possible to obtain a lock on a const Monitor object.
 *
 * @see TryLock
 **/
class Monitor
{
private:
    friend class LockGuard;
    friend class MonitorGuard;
    friend class TryLock;

    mutable FastOS_Cond _cond;
public:
    /**
     * @brief Create a new Monitor.
     *
     * Creates a Monitor that has mutex instrumentation disabled.
     **/
    Monitor() : _cond() {}
    /**
     * @brief Create a new Monitor.
     *
     * Creates a Monitor with support for mutex instrumentation.
     *
     * @param name mutex category (for instrumentation)
     * @param leaf false if you will lock other locks while holding this one,
     *             true if you only lock this as the last (leaf) lock
     *             (for instrumentation).
     **/
    Monitor(const char *name, bool leaf) : _cond() { (void) name; (void) leaf; }
    /**
     * @brief Copy a Monitor.
     *
     * Creates a new Monitor with mutex instrumentation settings
     * obtained from the given monitor. No other information is
     * copied.  Normally only used when copy-constructing a bigger
     * object containing a Lock.
     *
     * @param rhs other Monitor
     **/
    Monitor(const Monitor &rhs) : _cond() { (void) rhs; }
    /**
     * @brief No-op assignment operator.
     *
     * Assignment operator ignoring the right hand side. It makes no
     * sense to assign the state of one Monitor to another, but we want
     * to allow assigning objects that contain Monitor objects.
     *
     * @param rhs other Monitor (right hand side)
     **/
    Monitor &operator=(const Monitor &rhs) {
        (void) rhs;
        return *this;
    }
};


/**
 * @brief A TryLock object is used to try to obtain the lock on a Lock
 * or a Monitor without blocking.
 *
 * A TryLock will typically fail to obatin the lock if someone else
 * already has it. In that case, the TryLock object has no further
 * use.
 *
 * If the TryLock managed to acquire the lock, it can be passed over
 * to a LockGuard or MonitorGuard object. If the lock is not passed
 * on, the TryLock object will release it when it goes out of scope.
 *
 * Note that passing the lock obtained from a Lock to a MonitorGuard
 * is illegal. Also note that if the TryLock fails to aquire the lock,
 * it cannot be passed on. Trying to do so will result in an assert.
 *
 * copy/assignment of a TryLock is illegal.
 *
 * <pre>
 * Example:
 *
 * Lock lock;
 * TryLock tl(lock);
 * if (tl.hasLock()) {
 *   LockGuard guard(tl)
 *   ... do stuff
 * } // the lock is released as 'guard' goes out of scope
 * </pre>
 **/
class TryLock
{
private:
    friend class LockGuard;
    friend class MonitorGuard;

    FastOS_MutexInterface *_mutex;
    FastOS_CondInterface  *_cond;

    TryLock(const TryLock &);
    TryLock &operator=(const TryLock &);

    FastOS_MutexInterface *stealMutex() {
        FastOS_MutexInterface *ret = _mutex;
        if (ret != NULL) {
            _mutex = NULL;
            return ret;
        }
        ret = _cond;
        assert(ret != NULL);
        _cond = NULL;
        return ret;
    }
    FastOS_CondInterface *stealCond() {
        FastOS_CondInterface *ret = _cond;
        assert(ret != NULL);
        _cond = NULL;
        return ret;
    }

public:
    /**
     * @brief Try to obtain the lock represented by the given Lock object
     *
     * @param lock the lock to obtain
     **/
    TryLock(const Lock &lock) : _mutex(&lock._mutex), _cond(NULL) {
        if (!_mutex->TryLock()) {
            _mutex = NULL;
        }
    }
    /**
     * @brief Try to lock the given Monitor
     *
     * @param mon the monitor to lock
     **/
    TryLock(const Monitor &mon) : _mutex(NULL), _cond(&mon._cond) {
        if (!_cond->TryLock()) {
            _cond = NULL;
        }
    }
    /**
     * @brief Release the lock held by this object, if any
     **/
    ~TryLock() {
        if (_mutex != NULL) {
            _mutex->Unlock();
        }
        if (_cond != NULL) {
            _cond->Unlock();
        }
    }
    /**
     * @brief Check whether this object holds a lock
     *
     * @return true if this object holds a lock
     **/
    bool hasLock() { return (_mutex != NULL || _cond != NULL); }
    /**
     * @brief Release the lock held by this object.
     *
     * No methods may be invoked after invoking unlock (except the
     * destructor). Note that this method should only be used if you
     * need to release the lock before the object is destructed, as
     * the destructor will release the lock.
     **/
    void unlock() {
        if (_mutex != NULL) {
            _mutex->Unlock();
            _mutex = NULL;
        }
        if (_cond != NULL) {
            _cond->Unlock();
            _cond = NULL;
        }
    }
};


/**
 * @brief A LockGuard holds the lock on either a Lock or a Monitor.
 *
 * LockGuards are typically created on the stack to hold a lock within
 * a scope. If needed, the unlock method may be used to release the
 * lock before exiting scope.
 *
 * LockGuard has destructive copy. Copying a LockGuard has the
 * semantic of transferring the lock from one object to the
 * other. Note that assignment is not legal, and that copying a
 * LockGuard that does not have a lock will result in an assert.
 **/
class LockGuard
{
private:
    FastOS_MutexInterface *_mutex;
    LockGuard &operator=(const LockGuard &);

    FastOS_MutexInterface *stealMutex() {
        FastOS_MutexInterface *ret = _mutex;
        _mutex = NULL;
        return ret;
    }
public:
    /**
     * @brief A noop guard without any mutex.
     **/
    LockGuard() : _mutex(NULL) {}
    /**
     * @brief Steal the lock from the given LockGuard
     *
     * @param rhs steal the lock from this one
     **/
    LockGuard(LockGuard &rhs) : _mutex(rhs.stealMutex()) {}
    /**
     * @brief Obtain the lock represented by the given Lock object.
     *
     * The method will block until the lock can be obtained.
     *
     * @param lock take it
     **/
    LockGuard(const Lock &lock) : _mutex(&lock._mutex) {
        _mutex->Lock();
    }
    /**
     * @brief Obtain the lock on the given Monitor object.
     *
     * The method will block until the lock can be obtained.
     *
     * @param monitor take the lock on it
     **/
    LockGuard(const Monitor &monitor) : _mutex(&monitor._cond) {
        _mutex->Lock();
    }
    /**
     * @brief Create a LockGuard from a TryLock.
     *
     * The TryLock may have been created from either a Lock or a
     * Monitor, but it must have managed to acquire the lock. The lock
     * will be handed over from the TryLock to the new object.
     *
     * @param tlock take the lock from this one
     **/
    LockGuard(TryLock &tlock) : _mutex(tlock.stealMutex()) {}
#ifndef IAM_DOXYGEN
    LockGuard(const LockGuardHandover &rhs) : _mutex(rhs._mutex) {}
    operator LockGuardHandover() { return LockGuardHandover(stealMutex()); }
#endif
    /**
     * @brief Release the lock held by this object.
     *
     * No methods may be invoked after invoking unlock (except the
     * destructor). Note that this method should only be used if you
     * need to release the lock before the object is destructed, as
     * the destructor will release the lock.
     **/
    void unlock() {
        if (_mutex != NULL) {
            _mutex->Unlock();
            _mutex = NULL;
        }
    }
    /**
     * @brief Release the lock held by this object if unlock has not
     * been called.
     **/
    ~LockGuard() {
        if (_mutex != NULL) {
            _mutex->Unlock();
        }
    }

    /**
     * Allow code to match guard with lock. This allows functions to take a
     * guard ref as input, ensuring that the caller have grabbed a lock.
     */
    bool locks(const Lock& lock) const {
        return (_mutex != NULL && _mutex == &lock._mutex);
    }
};


/**
 * @brief A MonitorGuard holds the lock on a Monitor and supports
 * sending and waiting for signals.
 *
 * MonitorGuards are typically created on the stack to hold a lock
 * within a scope. If needed, the unlock method may be used to release
 * the lock before exiting scope.
 *
 * MonitorGuard has destructive copy. Copying a MonitorGuard has the
 * semantic of transferring the lock from one object to the
 * other. Note that assignment is not legal, and that copying a
 * MonitorGuard that does not have a lock will result in an assert.
 **/
class MonitorGuard
{
private:
    FastOS_CondInterface *_cond;
    MonitorGuard &operator=(const MonitorGuard &);

    FastOS_CondInterface *stealCond() {
        FastOS_CondInterface *ret = _cond;
        _cond = NULL;
        return ret;
    }
public:
    /**
     * @brief A noop guard without any condition.
     **/
    MonitorGuard() : _cond(NULL) {}
    /**
     * @brief Steal the lock from the given MonitorGuard
     *
     * @param rhs steal the lock from this one
     **/
    MonitorGuard(MonitorGuard &rhs) : _cond(rhs.stealCond()) {}
    /**
     * @brief Obtain the lock on the given Monitor object.
     *
     * The method will block until the lock can be obtained.
     *
     * @param monitor take the lock on it
     **/
    MonitorGuard(const Monitor &monitor) : _cond(&monitor._cond) {
        _cond->Lock();
    }
    /**
     * @brief Create a MonitorGuard from a TryLock.
     *
     * The TryLock must have been created from a Monitor, and it must
     * have managed to acquire the lock. The lock will be handed over
     * from the TryLock to the new object.
     *
     * @param tlock take the lock from this one
     **/
    MonitorGuard(TryLock &tlock) : _cond(tlock.stealCond()) {}
#ifndef IAM_DOXYGEN
    MonitorGuard(const MonitorGuardHandover &rhs) : _cond(rhs._cond) {}
    operator MonitorGuardHandover() {
        return MonitorGuardHandover(stealCond());
    }
#endif
    /**
     * @brief Release the lock held by this object.
     *
     * No methods may be invoked after invoking this one (except the
     * destructor). Note that this method should only be used if you
     * need to release the lock before this object is destructed, as
     * the destructor will release the lock.
     **/
    void unlock() {
        assert(_cond != NULL);
        _cond->Unlock();
        _cond = NULL;
    }
    /**
     * @brief Wait for a signal on the underlying Monitor.
     **/
    void wait() {
        assert(_cond != NULL);
        _cond->Wait();
    }
    /**
     * @brief Wait for a signal on the underlying Monitor with the
     * given timeout.
     *
     * @param msTimeout timeout in milliseconds
     * @return true if a signal was received, false if the wait timed out.
     **/
    bool wait(int msTimeout) {
        assert(_cond != NULL);
        return _cond->TimedWait(msTimeout);
    }
    /**
     * @brief Send a signal to a single waiter on the underlying
     * Monitor.
     **/
    void signal() {
        assert(_cond != NULL);
        _cond->Signal();
    }
    /**
     * @brief Send a signal to all waiters on the underlying Monitor.
     **/
    void broadcast() {
        assert(_cond != NULL);
        _cond->Broadcast();
    }
    /**
     * @brief Send a signal to a single waiter on the underlying
     * Monitor, but unlock the monitor right before doing so.
     *
     * This is inherently unsafe and the caller needs external
     * synchronization to ensure that the underlying Monitor object
     * will live long enough to be signaled.
     **/
    void unsafeSignalUnlock() {
        assert(_cond != NULL);
        _cond->Unlock();
        _cond->Signal();
        _cond = NULL;
    }
    /**
     * @brief Send a signal to all waiters on the underlying Monitor,
     * but unlock the monitor right before doing so.
     *
     * This is inherently unsafe and the caller needs external
     * synchronization to ensure that the underlying Monitor object
     * will live long enough to be signaled.
     **/
    void unsafeBroadcastUnlock() {
        assert(_cond != NULL);
        _cond->Unlock();
        _cond->Broadcast();
        _cond = NULL;
    }
    /**
     * @brief Release the lock held by this object if unlock has not
     * been called.
     **/
    ~MonitorGuard() {
        if (_cond != NULL) {
            _cond->Unlock();
        }
    }

    /**
     * Allow code to match guard with lock. This allows functions to take a
     * guard ref as input, ensuring that the caller have grabbed a lock.
     */
    bool monitors(const Monitor& m) const {
        return (_cond != NULL && _cond == &m._cond);
    }
};


/**
 * Helper class that can be used to wait for a condition when having a
 * constraint on how long you want to wait. The usage is best
 * explained with an example:
 *
 * <pre>
 * bool waitForWantedState(int maxwait) {
 *     MonitorGuard guard(_monitor);
 *     TimedWaiter waiter(guard, maxwait);
 *     while (!wantedState && waiter.hasTime()) {
 *         waiter.wait();
 *     }
 *     return wantedState;
 * }
 * </pre>
 *
 * The example code will limit the total wait time to maxwait
 * milliseconds across all blocking wait operations on the underlying
 * monitor guard.
 **/
class TimedWaiter
{
private:
    MonitorGuard &_guard;
    FastOS_Time   _start;
    int           _maxwait;
    int           _remain;
    bool          _timeout;

    TimedWaiter(const TimedWaiter&);
    TimedWaiter &operator=(const TimedWaiter&);
public:

    /**
     * Create a new instance using the given monitor guard and wait
     * time limit. If the maximum time is less than or equal to 0, the
     * wait will time out immediately and no low-level wait operations
     * will be performed.
     *
     * @param guard the underlying monitor guard used to perform the actual wait operation.
     * @param maxwait maximum time to wait in milliseconds.
     **/
    TimedWaiter(MonitorGuard &guard, int maxwait)
        : _guard(guard), _start(), _maxwait(maxwait), _remain(0), _timeout(false)
    {
        if (_maxwait > 0) {
            _start.SetNow();
        } else {
            _timeout = true;
        }
    }

    /**
     * Check if this object has any time left until the time limit is
     * exceeded.
     *
     * @return true if there is time left.
     **/
    bool hasTime() const {
        return !_timeout;
    }

    /**
     * Perform low-level wait in such a way that we do not exceed the
     * maximum total wait time. This method also performs the needed
     * book-keeping to keep track of elapsed time between invocations.
     *
     * @return true if we woke up due to a signal, false if we timed out.
     **/
    bool wait() {
        if (!_timeout) {
            if (_remain > 0) {
                _remain = (_maxwait - (int)_start.MilliSecsToNow());
            } else {
                _remain = _maxwait;
            }
            if (_remain > 0) {
                _timeout = !_guard.wait(_remain);
            } else {
                _timeout = true;
            }
        }
        return !_timeout;
    }
};


/**
 * A countdown latch helps one or more threads wait for the completion
 * of a number of operations performed by other threads. Specifically,
 * any thread invoking the await method will block until the countDown
 * method has been invoked an appropriate number of times. The
 * countdown latch is created with a count. Each invocation of
 * countDown will reduce the current count. When the count reaches 0,
 * the threads blocked in await will be unblocked. When the count is
 * 0, additional invocations of await will not block and additional
 * invocations of countDown will have no effect.
 **/
class CountDownLatch
{
private:
    Monitor  _monitor;
    uint32_t _count;

    CountDownLatch(const CountDownLatch &rhs);
    CountDownLatch &operator=(const CountDownLatch &rhs);

public:
    /**
     * Create a countdown latch with the given initial count.
     *
     * @param cnt initial count
     **/
    CountDownLatch(uint32_t cnt) : _monitor(), _count(cnt) {}

    /**
     * Count down this latch. When the count reaches 0, all threads
     * blocked in the await method will be unblocked.
     **/
    void countDown() {
        MonitorGuard guard(_monitor);
        if (_count == 0) {
            return;
        }
        --_count;
        if (_count == 0) {
            guard.broadcast();
        }
    }

    /**
     * Wait for this latch to count down to 0. This method will block
     * until the countDown method has been invoked enough times to
     * reduce the count to 0.
     **/
    void await() {
        MonitorGuard guard(_monitor);
        while (_count != 0) {
            guard.wait();
        }
    }

    /**
     * Wait for this latch to count down to 0. This method will block
     * until the countDown method has been invoked enough times to
     * reduce the count to 0 or the given amount of time has elapsed.
     *
     * @param maxwait the maximum number of milliseconds to wait
     * @return true if the counter reached 0, false if we timed out
     **/
    bool await(int maxwait) {
        MonitorGuard guard(_monitor);
        TimedWaiter waiter(guard, maxwait);
        while (_count != 0 && waiter.hasTime()) {
            waiter.wait();
        }
        return (_count == 0);
    }

    /**
     * Obtain the current count for this latch. This method is mostly
     * useful for debugging and testing.
     *
     * @return current count
     **/
    uint32_t getCount() const {
        return _count;
    }

    /**
     * Empty. Needs to be virtual to reduce compiler warnings.
     **/
    virtual ~CountDownLatch() {}
};


/**
 * A gate is a countdown latch with an initial count of 1, indicating
 * that we are only waiting for a single operation to complete.
 **/
class Gate : public CountDownLatch
{
public:
    /**
     * Sets the initial count to 1.
     **/
    Gate() : CountDownLatch(1) {}
};

} // namespace vespalib

