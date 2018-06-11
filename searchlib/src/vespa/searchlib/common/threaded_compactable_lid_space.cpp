// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cstddef>
#include <cstdint>
#include "threaded_compactable_lid_space.h"
#include "isequencedtaskexecutor.h"
#include <future>

namespace search::common {

ThreadedCompactableLidSpace::ThreadedCompactableLidSpace(std::shared_ptr<ICompactableLidSpace> target,
                                                         ISequencedTaskExecutor &executor,
                                                         ISequencedTaskExecutor::ExecutorId id)
    : _target(target),
      _executor(executor),
      _executorId(id)
{
}

ThreadedCompactableLidSpace::~ThreadedCompactableLidSpace() = default;

void
ThreadedCompactableLidSpace::compactLidSpace(uint32_t wantedDocLidLimit)
{
    std::promise<void> promise;
    auto future = promise.get_future();
    _executor.executeLambda(_executorId, [this, wantedDocLidLimit, &promise]() { _target->compactLidSpace(wantedDocLidLimit); promise.set_value(); });
    future.wait();
}

bool
ThreadedCompactableLidSpace::canShrinkLidSpace() const
{
    return _target->canShrinkLidSpace();
}

size_t
ThreadedCompactableLidSpace::getEstimatedShrinkLidSpaceGain() const
{
    return _target->getEstimatedShrinkLidSpaceGain();
}

void
ThreadedCompactableLidSpace::shrinkLidSpace()
{
    std::promise<void> promise;
    auto future = promise.get_future();
    _executor.executeLambda(_executorId, [this, &promise]() { _target->shrinkLidSpace(); promise.set_value(); });
    future.wait();
}

}
