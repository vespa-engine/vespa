// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>

namespace proton {

/**
 * Implements a flush target that shrinks lid space in target. Used for document meta store.
 */

class ShrinkDocumentMetaStoreLidSpaceFlushTarget : public ShrinkLidSpaceFlushTarget {
    using ICompactableLidSpace = search::common::ICompactableLidSpace;

public:
    ShrinkDocumentMetaStoreLidSpaceFlushTarget(std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in,
                                               SerialNum flushedSerialNum, Time last_flush_time,
                                               std::shared_ptr<ICompactableLidSpace> target);
    ~ShrinkDocumentMetaStoreLidSpaceFlushTarget() override;
    [[nodiscard]] std::string_view trace_span_name() const noexcept override;
};

} // namespace proton
