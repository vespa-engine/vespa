// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_compactable_lid_space.h"
#include "isequencedtaskexecutor.h"
#include <memory>

namespace search::common {

/**
 * Adapter class for a component that has a lid space that can be
 * compacted and shrunk where the write operations are performed by a
 * specific thread.
 */
class ThreadedCompactableLidSpace : public ICompactableLidSpace
{
    std::shared_ptr<ICompactableLidSpace> _target;
    ISequencedTaskExecutor               &_executor;
    ISequencedTaskExecutor::ExecutorId    _executorId;
public:
    ThreadedCompactableLidSpace(std::shared_ptr<ICompactableLidSpace> target, ISequencedTaskExecutor &executor,
                                ISequencedTaskExecutor::ExecutorId executorId);
    ~ThreadedCompactableLidSpace() override;
    void compactLidSpace(uint32_t wantedDocLidLimit) override;
    bool canShrinkLidSpace() const override;
    size_t getEstimatedShrinkLidSpaceGain() const override;
    void shrinkLidSpace() override;
};

}
