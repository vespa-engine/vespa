// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "executor.h"
#include "syncable.h"

namespace vespalib {

class ThreadExecutor : public Executor
{
public:
    /**
     * Get number of threads in the executor pool.
     * @return number of threads in the pool
     */
    virtual size_t getNumThreads() const = 0;
};

/**
 * Can both execute and sync
 **/
class SyncableThreadExecutor : public ThreadExecutor, public Syncable
{
public:
};

} // namespace vespalib

