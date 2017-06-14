// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definitions for FastOS_CondInterface and FastOS_BoolCond.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once


#include <vespa/fastos/types.h>
#include <vespa/fastos/mutex.h>


/**
 * This class implements a synchronization mechanism used by threads to wait
 * until a condition expression involving shared data attains a particular state.
 *
 * Condition variables provide a different type of synchronization
 * than locking mechanisms like mutexes. For instance, a mutex is used
 * to cause other threads to wait while the thread holding the mutex
 * executes code in a critical section.  In contrast, a condition
 * variable is typically used by a thread to make itself wait until an
 * expression involving shared data attains a particular state.
 */
class FastOS_CondInterface : public FastOS_Mutex
{
public:
    FastOS_CondInterface(void) : FastOS_Mutex() { }

    virtual ~FastOS_CondInterface () {}

    /**
     * Wait for the condition to be signalled. If the wait takes
     * longer than [milliseconds] ms, the wait is aborted and false
     * is returned.
     * @param  milliseconds   Max time to wait.
     * @return                Boolean success/failure
     */
    virtual bool TimedWait (int milliseconds) = 0;

    /**
     * Wait for the condition to be signalled.
     */
    virtual void Wait (void)=0;

    /**
     * Send a signal to one thread waiting on the condition (if any).
     */
    virtual void Signal (void)=0;

    /**
     * Send a signal to all threads waiting on the condition.
     */
    virtual void Broadcast (void)=0;
};

#include <vespa/fastos/unix_cond.h>
typedef FastOS_UNIX_Cond FASTOS_PREFIX(Cond);

/**
 * This class implements a condition variable with a boolean
 * value.
 */
class FastOS_BoolCond : public FastOS_Cond
{
    bool _busy;

public:
    /**
     * Constructor. Initially the boolean variable is
     * set to non-busy.
     */
    FastOS_BoolCond(void) : _busy(false) { }

    ~FastOS_BoolCond(void) { }

    /**
     * If the variable is busy, wait for it to be non-busy,
     * then set the variable to busy. */
    void SetBusy(void)
    {
        Lock();

        while (_busy == true)
            Wait();

        _busy = true;
        Unlock();
    }

    /**
     * If the variable is busy, wait until it is no longer busy.
     * If it was non-busy to begin with, no wait is performed.
     */
    void WaitBusy(void)
    {
        Lock();

        while (_busy == true)
            Wait();

        Unlock();
    }

    /**
     * If the variable is busy, wait until it is no longer busy or a
     * timeout occurs.  If it was non-busy to begin with, no wait is
     * performed.
     * @param ms Time to wait
     * @return True=non-busy, false=timeout
     */
    bool TimedWaitBusy(int ms)
    {
        bool success = true;

        Lock();
        if (_busy == true) {
            success = TimedWait(ms);
        }
        Unlock();

        return success;
    }

    /**
     * Return busy status.
     * @return  True=busy, false=non-busy
     */
    bool PollBusy (void)
    {
        bool rc;
        Lock();
        rc = _busy;
        Unlock();
        return rc;
    }

    /**
     * Set the variable to non-busy, and signal one thread
     * waiting (if there are any).
     * (if any).
     */
    void ClearBusy(void)
    {
        Lock();
        _busy = false;
        Signal();
        Unlock();
    }

    /**
     * Set the variable to non-busy, and broadcast to all
     * threads waiting (if there are any).
     */
    void ClearBusyBroadcast(void)
    {
        Lock();
        _busy = false;
        Broadcast();
        Unlock();
    }
};


