// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/threadstackexecutorbase.h>

namespace proton {

namespace internal { class ThreadId; }
/**
 * Implementation of IThreadService using an underlying thread stack executor
 * with a single thread.
 */
class ExecutorThreadService : public searchcorespi::index::IThreadService
{
private:
    vespalib::ThreadStackExecutorBase &_executor;
    std::unique_ptr<internal::ThreadId>   _threadId;

public:
    ExecutorThreadService(vespalib::ThreadStackExecutorBase &executor);
    ~ExecutorThreadService();

    /**
     * Implements IThreadService
     */
    virtual vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) override {
        return _executor.execute(std::move(task));
    }
    virtual void run(vespalib::Runnable &runnable) override;
    virtual vespalib::Syncable &sync() override {
        _executor.sync();
        return *this;
    }
    virtual bool isCurrentThread() const override;
};

} // namespace proton


