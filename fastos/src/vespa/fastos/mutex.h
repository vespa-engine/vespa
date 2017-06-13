// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definition for FastOS_Mutex.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once

#include <vespa/fastos/types.h>


/**
 * This class defines a mutual-exclusion object.
 *
 * Facilitates synchronized access to mutual-exclusion zones in the program.
 * Before entering code sections where only a single thread at the time can
 * operate, use @ref Lock(). If another thread is holding the lock at the
 * time, the calling thread will sleep until the current holder of the mutex
 * is through using it.
 *
 * Use @ref Unlock() to release the mutex lock. This will allow other threads
 * to obtain the lock.
 */

class FastOS_MutexInterface
{
public:
    /**
     * Destructor
     */
    virtual ~FastOS_MutexInterface () {};

    /**
     * Obtain an exclusive lock on the mutex. The result of a recursive lock
     * is currently undefined. The caller should assume this will result
     * in a deadlock situation.
     * A recursive lock occurs when a thread, currently owning the lock,
     * attempts to lock the mutex a second time.
     *
     * Use @ref Unlock() to unlock the mutex when done.
     */
    virtual void Lock (void)=0;

    /**
     * Try to obtain an exclusive lock on the mutex. If a lock cannot be
     * obtained right away, the method will return false. There will
     * be no blocking/waiting for the mutex lock to be available. If
     * the mutex was locked in the attempt, true is returned.
     * @return              Boolean success/failure
     */
    virtual bool TryLock (void)=0;

    /**
     * Unlock a locked mutex. The result of unlocking a mutex not already
     * locked by the calling thread is undefined.
     */
    virtual void Unlock (void)=0;
};

#include <vespa/fastos/unix_mutex.h>
typedef FastOS_UNIX_Mutex FASTOS_PREFIX(Mutex);

