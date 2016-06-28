// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".common.sequencedtaskexecutor");

#include "sequencedtaskexecutor.h"
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>

using vespalib::BlockingThreadStackExecutor;

namespace search
{

namespace
{

constexpr uint32_t stackSize = 128 * 1024;

}


SequencedTaskExecutor::SequencedTaskExecutor(uint32_t threads, uint32_t tasklimit)
    : _executors()
{
    for (uint32_t id = 0; id < threads; ++id) {
        auto executor = std::make_unique<BlockingThreadStackExecutor>(1, stackSize, tasklimit);
        _executors.push_back(std::move(executor));
    }
}

SequencedTaskExecutor::~SequencedTaskExecutor()
{
    sync();
}


void
SequencedTaskExecutor::executeTask(uint64_t id,
                                   vespalib::Executor::Task::UP task)
{
    auto itr = _ids.find(id);
    if (itr == _ids.end()) {
        auto insarg = std::make_pair(id, _ids.size() % _executors.size());
        auto insres = _ids.insert(insarg);
        assert(insres.second);
        itr = insres.first;
    }
    size_t executorId = itr->second;
    vespalib::ThreadStackExecutorBase &executor(*_executors[executorId]);
    auto rejectedTask = executor.execute(std::move(task));
    assert(!rejectedTask);
}


void
SequencedTaskExecutor::sync()
{
    for (auto &executor : _executors) {
        executor->sync();
    }
}


} // namespace search
