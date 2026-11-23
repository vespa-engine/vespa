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
    ShrinkSummaryLidSpaceFlushTarget(std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in,
                                     SerialNum flushedSerialNum, vespalib::Executor& summaryService,
                                     std::shared_ptr<ICompactableLidSpace> target);
    ~ShrinkSummaryLidSpaceFlushTarget() override;
    void init_flush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token,
                    TaskPromise task_promise) override;
    [[nodiscard]] std::string_view trace_span_name() const noexcept override;
};

} // namespace proton
