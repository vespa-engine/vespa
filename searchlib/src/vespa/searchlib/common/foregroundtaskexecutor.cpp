// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "foregroundtaskexecutor.h"
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespalib::ThreadStackExecutor;

namespace search {

ForegroundTaskExecutor::ForegroundTaskExecutor()
    : ForegroundTaskExecutor(1)
{
}

ForegroundTaskExecutor::ForegroundTaskExecutor(uint32_t threads)
    : _threads(threads),
      _ids()
{
}

ForegroundTaskExecutor::~ForegroundTaskExecutor()
{
}

uint32_t
ForegroundTaskExecutor::getExecutorId(uint64_t componentId)
{
    auto itr = _ids.find(componentId);
    if (itr == _ids.end()) {
        auto insarg = std::make_pair(componentId, _ids.size() % _threads);
        auto insres = _ids.insert(insarg);
        assert(insres.second);
        itr = insres.first;
    }
    return itr->second;
}

void
ForegroundTaskExecutor::executeTask(uint32_t executorId, vespalib::Executor::Task::UP task)
{
    assert(executorId < _threads);
    task->run();
}


void
ForegroundTaskExecutor::sync()
{
}


} // namespace search
