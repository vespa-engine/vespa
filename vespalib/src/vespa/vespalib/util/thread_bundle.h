// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "runnable.h"
#include <vector>

namespace vespalib {

/**
 * Interface used to separate the ownership and deployment of a
 * collection of threads cooperating to perform a partitioned
 * operation in parallel.
 **/
struct ThreadBundle {
    /**
     * The size of the thread bundle is defined to be the maximum
     * number of runnables that can be performed in parallel by the
     * run function.
     *
     * @return size of this thread bundle
     **/
    virtual size_t size() const = 0;

    /**
     * Performs all the given runnables in parallel and waits for
     * their completion. This function cannot be called with more
     * targets than the size of this bundle.
     **/
    virtual void run(const std::vector<Runnable*> &targets) = 0;

    /**
     * Empty virtual destructor to enable subclassing.
     **/
    virtual ~ThreadBundle() {}
};

} // namespace vespalib

