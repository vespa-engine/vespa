// Copyright 2020 Oath inc.. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "isequencedtaskexecutor.h"
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/hashtable.h>
#include <cassert>

namespace search {
    namespace {
        constexpr uint8_t MAGIC = 255;
    }

ISequencedTaskExecutor::ISequencedTaskExecutor(uint32_t numExecutors)
    : _component2Id(vespalib::hashtable_base::getModuloStl(numExecutors*8), MAGIC),
      _mutex(),
      _numExecutors(numExecutors),
      _nextId(0)
{
    assert(numExecutors < 256);
}

ISequencedTaskExecutor::~ISequencedTaskExecutor() = default;

ISequencedTaskExecutor::ExecutorId
ISequencedTaskExecutor::getExecutorId(vespalib::stringref componentId) const {
    vespalib::hash<vespalib::stringref> hashfun;
    return getExecutorId(hashfun(componentId));
}

ISequencedTaskExecutor::ExecutorId
ISequencedTaskExecutor::getExecutorId(uint64_t componentId) const {
    uint32_t shrunkId = componentId % _component2Id.size();
    uint8_t executorId = _component2Id[shrunkId];
    if (executorId == MAGIC) {
        std::lock_guard guard(_mutex);
        if (_component2Id[shrunkId] == MAGIC) {
            _component2Id[shrunkId] = _nextId % getNumExecutors();
            _nextId++;
        }
        executorId = _component2Id[shrunkId];
    }
    return ExecutorId(executorId);
}

}
