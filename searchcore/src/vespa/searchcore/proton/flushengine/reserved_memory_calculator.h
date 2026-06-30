// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reserved_resource_candidates.h"

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton::flushengine {

class ReservedMemoryCalculator {
    size_t                             _each_max_memory;
    size_t                             _global_max_memory;
    uint32_t                           _memory_indexes;
    ReservedResourceCandidates<size_t> _candidates;

public:
    using IFlushTarget = searchcorespi::IFlushTarget;
    ReservedMemoryCalculator(size_t concurrent, size_t each_max_memory, size_t global_max_memory) noexcept;
    ~ReservedMemoryCalculator();
    void track_reserved_memory_for_flush(size_t reserved_memory_for_flush, IFlushTarget::Type type,
                                         IFlushTarget::Component component, bool high_priority);
    [[nodiscard]] size_t reserved_memory_for_flush();
    [[nodiscard]] size_t reserved_memory_for_memory_indexes() const noexcept;
};

} // namespace proton::flushengine
