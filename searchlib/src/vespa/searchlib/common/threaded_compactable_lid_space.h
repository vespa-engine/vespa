// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_compactable_lid_space.h"
#include <memory>

namespace search {

class ISequencedTaskExecutor;

namespace common {

/**
 * Adapter class for a component that has a lid space that can be
 * compacted and shrunk where the write operations are performed by a
 * specific thread.
 */
class ThreadedCompactableLidSpace : public ICompactableLidSpace
{
    std::shared_ptr<ICompactableLidSpace> _target;
    ISequencedTaskExecutor &_executor;
    uint32_t                _executorId;
public:
    ThreadedCompactableLidSpace(std::shared_ptr<ICompactableLidSpace> target, ISequencedTaskExecutor &executor, uint32_t executorId);
    virtual ~ThreadedCompactableLidSpace() override;
    virtual void compactLidSpace(uint32_t wantedDocLidLimit) override;
    virtual bool canShrinkLidSpace() const override;
    virtual size_t getEstimatedShrinkLidSpaceGain() const override;
    virtual void shrinkLidSpace() override;
};

}
}
