// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::ThreadPool
 * \ingroup thread
 *
 * \brief A threadpool implementation usable by storage components.
 *
 * Using this threadpool interface, we can use a threadpool without depending
 * on the actual implementation. Also, as information is provided of the
 * threads, monitoring tools, like the deadlock detector can extract information
 * about the threads.
 */
#pragma once

#include <atomic>
#include <vespa/storageframework/generic/thread/runnable.h>
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storageframework/generic/clock/time.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <optional>
#include <vector>

namespace storage::framework {

/** Interface used to access data for the existing threads. */
struct ThreadVisitor {
    virtual ~ThreadVisitor() = default;
    virtual void visitThread(const Thread& thread) = 0;
};

struct ThreadPool {
    virtual ~ThreadPool() = default;

    virtual Thread::UP startThread(Runnable&,
                                   vespalib::stringref id,
                                   vespalib::duration waitTime,
                                   vespalib::duration maxProcessTime,
                                   int ticksBeforeWait,
                                   std::optional<vespalib::CpuUsage::Category> cpu_category) = 0;

    virtual void visitThreads(ThreadVisitor&) const = 0;
};

}
