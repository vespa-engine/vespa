// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2010 Yahoo

#pragma once

#include "sync.h"

namespace vespalib {

/**
 * Reusable barrier with a predefined number of participants.
 **/
class Barrier
{
private:
    size_t  _n;
    Monitor _monitor;
    size_t  _count;
    size_t  _next;

public:
    /**
     * Create a new barrier with the given number of participants
     *
     * @param n number of participants
     **/
    Barrier(size_t n) : _n(n), _monitor(), _count(0), _next(0) {}

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

