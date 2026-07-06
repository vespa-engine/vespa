// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_resource_candidates.hpp"

#include "reserved_memory_calculator.h"

namespace proton::flushengine {

template class ReservedResourceCandidates<size_t>;
#ifdef __APPLE__
template class ReservedResourceCandidates<uint64_t>;
#endif

} // namespace proton::flushengine
