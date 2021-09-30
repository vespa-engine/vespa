// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class DiskThread
 * @ingroup persistence
 *
 * @brief Implements the public API of the disk threads.
 *
 * The disk threads have a tiny interface as they pull messages of the disk
 * queue themselves. Thus it is easy to provide multiple implementations of it.
 * The diskthread implements the common functionality needed above, currently
 * for the filestor manager.
 */
#pragma once

#include <vespa/storageframework/generic/thread/runnable.h>
#include <memory>

namespace storage::framework { class Thread; }

namespace storage {

class DiskThread : public framework::Runnable
{
public:
    typedef std::shared_ptr<DiskThread> SP;

    DiskThread(const DiskThread &) = delete;
    DiskThread & operator = (const DiskThread &) = delete;
    DiskThread() = default;
    virtual ~DiskThread() = default;
    /** Waits for current operation to be finished. */
    virtual void flush() = 0;

    virtual framework::Thread& getThread() = 0;

};

}

