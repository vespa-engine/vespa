// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cachedflushtarget.h"

namespace proton {

CachedFlushTarget::CachedFlushTarget(const IFlushTarget::SP &target)
    : IFlushTarget(target->getName(), target->getType(), target->getComponent()),
      _target(target),
      _flushedSerialNum(target->getFlushedSerialNum()),
      _lastFlushTime(target->getLastFlushTime()),
      _memoryGain(target->getApproxMemoryGain()),
      _diskGain(target->getApproxDiskGain()),
      _approxBytesToWriteToDisk(target->getApproxBytesToWriteToDisk()),
      _approx_bytes_to_read_from_disk(target->get_approx_bytes_to_read_from_disk()),
      _replay_operation_cost(target->get_replay_operation_cost()),
      _needUrgentFlush(target->needUrgentFlush()),
      _priority(target->getPriority())
{ }

CachedFlushTarget::~CachedFlushTarget() = default;

uint64_t
CachedFlushTarget::get_approx_bytes_to_read_from_disk() const noexcept
{
    return _approx_bytes_to_read_from_disk;
}

} // namespace proton
