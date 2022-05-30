// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/runnable.h>
#include <vespa/vespalib/util/threadexecutor.h>

namespace searchcorespi::index {

/**
 * Interface for a single thread used for write tasks.
 */
struct IThreadService : public vespalib::ThreadExecutor
{
    IThreadService(const IThreadService &) = delete;
    IThreadService & operator = (const IThreadService &) = delete;
    IThreadService() = default;
    virtual ~IThreadService() {}

    /**
     * Run the given runnable in the underlying thread and wait until its done.
     */
    virtual void run(vespalib::Runnable &runnable) = 0;

    /**
     * Returns whether the current thread is the underlying thread.
     */
    virtual bool isCurrentThread() const = 0;
};

struct ISyncableThreadService : public IThreadService, vespalib::Syncable {

};

}
