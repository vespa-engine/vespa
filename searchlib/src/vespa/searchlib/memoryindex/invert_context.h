// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bundled_fields_context.h"

namespace search::memoryindex {

/*
 * Context used by an InvertTask to invert a set of document fields
 * into corresponding field inverters or by a RemoveTask to remove
 * documents from a set of field inverters.
 *
 * It is also used by DocumentInverter::pushDocuments() to execute
 * PushTask at the proper time (i.e. when all related InvertTask /
 * RemoveTask operations have completed).
 */
class InvertContext : public BundledFieldsContext
{
    std::vector<uint32_t> _pushers;
public:
    void add_pusher(uint32_t pusher_id);
    InvertContext(vespalib::ISequencedTaskExecutor::ExecutorId id);
    ~InvertContext();
    const std::vector<uint32_t>& get_pushers() const noexcept { return _pushers; }
};

}
