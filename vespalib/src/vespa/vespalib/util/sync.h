// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cassert>
#include <mutex>
#include <condition_variable>
#include <chrono>

namespace vespalib {

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
protected:
    friend class LockGuard;
    friend class TryLock;

    mutable std::mutex _mutex;
public:
    /**
     * @brief Create a new Lock.
     *
     * Creates a Lock that has mutex instrumentation disabled.
     **/
    Lock() : _mutex() {}
    Lock(const Lock &) : Lock() { }
    Lock(Lock &&) : Lock() { }
    Lock &operator=(const Lock &) { return *this; }
    Lock &operator=(Lock &&) { return *this; }
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
class Monitor : public Lock
{
private:
    friend class LockGuard;
    friend class MonitorGuard;
    friend class TryLock;

    mutable std::condition_variable _cond;
public:
    /**
     * @brief Create a new Monitor.
     *
     * Creates a Monitor that has mutex instrumentation disabled.
     **/
    Monitor() : Lock(), _cond() {}
    Monitor(const Monitor &) : Monitor() { }
    Monitor(Monitor &&) : Monitor() { }
    Monitor &operator=(const Monitor &) { return *this; }
    Monitor &operator=(Monitor &&) { return *this; }
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

    std::unique_lock<std::mutex> _guard;
    std::condition_variable     *_cond;

    TryLock(const TryLock &) = delete;
    TryLock &operator=(const TryLock &) = delete;

public:
    /**
     * @brief Try to obtain the lock represented by the given Lock object
     *
     * @param lock the lock to obtain
     **/
    TryLock(const Lock &lock)
        : _guard(lock._mutex, std::try_to_lock),
          _cond(nullptr)
    {
    }

    /**
     * @brief Try to lock the given Monitor
     *
     * @param mon the monitor to lock
     **/
    TryLock(const Monitor &mon)
        : _guard(mon._mutex, std::try_to_lock),
          _cond(_guard ? &mon._cond : nullptr)
    {
    }

    TryLock(TryLock &&rhs)
        : _guard(std::move(rhs._guard)),
          _cond(rhs._cond)
    {
        rhs._cond = nullptr;
    }

    /**
     * @brief Release the lock held by this object, if any
     **/
    ~TryLock() = default;

    TryLock &operator=(TryLock &&rhs) {
        if (this != &rhs) {
            _guard = std::move(rhs._guard);
            _cond = rhs._cond;
            rhs._cond = nullptr;
        }
        return *this;
    }

    /**
     * @brief Check whether this object holds a lock
     *
     * @return true if this object holds a lock
     **/
    bool hasLock() { return static_cast<bool>(_guard); }
    /**
     * @brief Release the lock held by this object.
     *
     * No methods may be invoked after invoking unlock (except the
     * destructor). Note that this method should only be used if you
     * need to release the lock before the object is destructed, as
     * the destructor will release the lock.
     **/
    void unlock() {
        if (_guard) {
            _guard.unlock();
            _cond = nullptr;
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
    std::unique_lock<std::mutex> _guard;
    LockGuard &operator=(const LockGuard &) = delete;
public:
    /**
     * @brief A noop guard without any mutex.
     **/
    LockGuard() : _guard() {}
    LockGuard(const LockGuard &rhs) = delete;
    /**
     * @brief Steal the lock from the given LockGuard
     *
     * @param rhs steal the lock from this one
     **/
    LockGuard(LockGuard &&rhs) : _guard(std::move(rhs._guard)) { }
    /**
     * @brief Obtain the lock represented by the given Lock object.
     *
     * The method will block until the lock can be obtained.
     *
     * @param lock take it
     **/
    LockGuard(const Lock &lock) : _guard(lock._mutex) { }

    /**
     * @brief Create a LockGuard from a TryLock.
     *
     * The TryLock may have been created from either a Lock or a
     * Monitor, but it must have managed to acquire the lock. The lock
     * will be handed over from the TryLock to the new object.
     *
     * @param tlock take the lock from this one
     **/
    LockGuard(TryLock &&tlock) : _guard(std::move(tlock._guard))
    {
        tlock._cond = nullptr;
    }

    LockGuard &operator=(LockGuard &&rhs) {
        if (this != &rhs) {
            _guard = std::move(rhs._guard);
        }
        return *this;
    }

    /**
     * @brief Release the lock held by this object.
     *
     * No methods may be invoked after invoking unlock (except the
     * destructor). Note that this method should only be used if you
     * need to release the lock before the object is destructed, as
     * the destructor will release the lock.
     **/
    void unlock() {
        if (_guard) {
            _guard.unlock();
        }
    }
    /**
     * @brief Release the lock held by this object if unlock has not
     * been called.
     **/
    ~LockGuard() = default;

    /**
     * Allow code to match guard with lock. This allows functions to take a
     * guard ref as input, ensuring that the caller have grabbed a lock.
     */
    bool locks(const Lock& lock) const {
        return (_guard && _guard.mutex() == &lock._mutex);
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
    std::unique_lock<std::mutex> _guard;
    std::condition_variable *_cond;
    MonitorGuard &operator=(const MonitorGuard &) = delete;

public:
    /**
     * @brief A noop guard without any condition.
     **/
    MonitorGuard() : _guard(), _cond(nullptr) {}
    MonitorGuard(const MonitorGuard &rhs) = delete;
    /**
     * @brief Steal the lock from the given MonitorGuard
     *
     * @param rhs steal the lock from this one
     **/
    MonitorGuard(MonitorGuard &&rhs)
        : _guard(std::move(rhs._guard)),
          _cond(rhs._cond)
    {
        rhs._cond = nullptr;
    }
    /**
     * @brief Obtain the lock on the given Monitor object.
     *
     * The method will block until the lock can be obtained.
     *
     * @param monitor take the lock on it
     **/
    MonitorGuard(const Monitor &monitor)
        : _guard(monitor._mutex),
          _cond(&monitor._cond)
    {
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
    MonitorGuard(TryLock &&tlock)
        : _guard(),
          _cond(nullptr)
    {
        if (tlock._guard && tlock._cond != nullptr) {
            _guard = std::move(tlock._guard);
            _cond = tlock._cond;
            tlock._cond = nullptr;
        }
    }

    MonitorGuard &operator=(MonitorGuard &&rhs) {
        if (this != &rhs) {
            _guard = std::move(rhs._guard);
            _cond = rhs._cond;
            rhs._cond = nullptr;
        }
        return *this;
    }


    /**
     * @brief Release the lock held by this object.
     *
     * No methods may be invoked after invoking this one (except the
     * destructor). Note that this method should only be used if you
     * need to release the lock before this object is destructed, as
     * the destructor will release the lock.
     **/
    void unlock() {
        assert(_guard);
        _guard.unlock();
        _cond = nullptr;
    }
    /**
     * @brief Wait for a signal on the underlying Monitor.
     **/
    void wait() {
        _cond->wait(_guard);
    }
    /**
     * @brief Wait for a signal on the underlying Monitor with the
     * given timeout.
     *
     * @param msTimeout timeout in milliseconds
     * @return true if a signal was received, false if the wait timed out.
     **/
    bool wait(int msTimeout) {
        return wait(std::chrono::milliseconds(msTimeout));
    }
    bool wait(std::chrono::nanoseconds timeout) {
        return _cond->wait_for(_guard, timeout) == std::cv_status::no_timeout;
    }
    /**
     * @brief Send a signal to a single waiter on the underlying
     * Monitor.
     **/
    void signal() {
        _cond->notify_one();
    }
    /**
     * @brief Send a signal to all waiters on the underlying Monitor.
     **/
    void broadcast() {
        _cond->notify_all();
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
        _guard.unlock();
        _cond->notify_one();
        _cond = nullptr;
    }

    /**
     * @brief Release the lock held by this object if unlock has not
     * been called.
     **/
    ~MonitorGuard() = default;

    /**
     * Allow code to match guard with lock. This allows functions to take a
     * guard ref as input, ensuring that the caller have grabbed a lock.
     */
    bool monitors(const Monitor& m) const {
        return (_cond != nullptr && _cond == &m._cond);
    }
};

} // namespace vespalib

