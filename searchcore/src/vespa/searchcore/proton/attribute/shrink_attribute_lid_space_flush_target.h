// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>

namespace proton {

/**
 * Implements a flush target that shrinks lid space in target. Used for attribute vectors.
 */

class ShrinkAttributeLidSpaceFlushTarget : public ShrinkLidSpaceFlushTarget {
    using ICompactableLidSpace = search::common::ICompactableLidSpace;
    using ISequencedTaskExecutor = vespalib::ISequencedTaskExecutor;

    ISequencedTaskExecutor&            _executor;
    ISequencedTaskExecutor::ExecutorId _executor_id;

public:
    ShrinkAttributeLidSpaceFlushTarget(const std::string& name, Type type, Component component,
                                       SerialNum flushedSerialNum, std::shared_ptr<ICompactableLidSpace> target,
                                       ISequencedTaskExecutor&            executor,
                                       ISequencedTaskExecutor::ExecutorId executor_id);
    ~ShrinkAttributeLidSpaceFlushTarget() override;
    void init_flush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token,
                    TaskPromise task_promise) override;
};

} // namespace proton
