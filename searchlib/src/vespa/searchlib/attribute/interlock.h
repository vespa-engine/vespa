// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>

namespace search::attribute {

class InterlockGuard;

/**
 * Class used to serialize getting enum change exclusive lock.  This
 * eliminates the need for defining a locking order when getting enum
 * change shared locks.  Scenario avoided is:
 *
 * Threads T1, T2: Grouping queries
 * Threads T3, T4: Attribute writer threads
 *
 * Thread T1 gets shared lock on A1
 * Thread T2 gets shared lock on A2
 * Theead T3 tries to get exclusive lock on A1
 * Theead T4 tries to get exclusive lock on A2
 * Thread T1 tries to get shared lock on A2
 * Thread T2 tries to get shared lock on A1
 *
 * With the interlock properly used, thread T3 will hold the
 * interlock, preventing thread T4 from registering intent to get
 * write lock on A2, thus thread T1 can get a shared lock on A2 and complete.
 */
class Interlock {
    std::mutex _mutex;
    friend class InterlockGuard;
public:
    Interlock() noexcept
        : _mutex()
    {
    }

    virtual ~Interlock() { }
};

/**
 * Class used to serialize getting enum change exclusive lock.  The guard
 * is passed to EnumModifier constructor to signal that interlock is held.
 */
class InterlockGuard
{
    std::lock_guard<std::mutex> _guard;
public:
    InterlockGuard(Interlock &interlock)
        : _guard(interlock._mutex)
    {
    }

    ~InterlockGuard() { }
};


}
