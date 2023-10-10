// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <condition_variable>

namespace vespalib {

/**
 * Reusable barrier with a predefined number of participants.
 **/
class Barrier
{
private:
    size_t                  _n;
    std::mutex              _lock;
    std::condition_variable _cond;
    size_t                  _count;
    size_t                  _next;

public:
    /**
     * Create a new barrier with the given number of participants
     *
     * @param n number of participants
     **/
    Barrier(size_t n);
    ~Barrier();

    /**
     * Wait for the (n - 1) other participants to call this
     * function. This function can be called multiple times.
     *
     * @return false if this barrier has been destroyed
     **/
    bool await();

    /**
     * Destroy this barrier, making all current and future calls to
     * await return false without waiting. A barrier is tagged as
     * destroyed by setting the number of participants to 0.
     **/
    void destroy();
};

} // namespace vespalib

