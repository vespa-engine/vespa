// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "isequencedtaskexecutor.h"
#include <vespa/vespalib/stllike/hash_fun.h>

namespace vespalib {

ISequencedTaskExecutor::ISequencedTaskExecutor(uint32_t numExecutors)
    : _numExecutors(numExecutors)
{
}

ISequencedTaskExecutor::~ISequencedTaskExecutor() = default;

ISequencedTaskExecutor::ExecutorId
ISequencedTaskExecutor::getExecutorIdFromName(vespalib::stringref componentId) const {
    vespalib::hash<vespalib::stringref> hashfun;
    return getExecutorId(hashfun(componentId));
}

}
