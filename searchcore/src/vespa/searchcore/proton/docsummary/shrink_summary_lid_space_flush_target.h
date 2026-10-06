// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>

namespace proton {

/**
 * Implements a flush target that shrinks lid space in target. Used for docstore.
 */

class ShrinkSummaryLidSpaceFlushTarget : public ShrinkLidSpaceFlushTarget {
    using ICompactableLidSpace = search::common::ICompactableLidSpace;
    vespalib::Executor& _summaryService;

public:
    ShrinkSummaryLidSpaceFlushTarget(const std::string& name, Type type, Component component,
                                     SerialNum flushedSerialNum, vespalib::Executor& summaryService,
                                     std::shared_ptr<ICompactableLidSpace> target);
    ~ShrinkSummaryLidSpaceFlushTarget() override;
    void init_flush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token,
                    TaskPromise task_promise) override;
};

} // namespace proton
