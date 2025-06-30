// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushtargetproxy.h"

namespace proton {

using searchcorespi::IFlushTarget;
using searchcorespi::FlushStats;

FlushTargetProxy::FlushTargetProxy(const IFlushTarget::SP &target)
    : IFlushTarget(target->getName(), target->getType(),
                   target->getComponent()),
      _target(target)
{
}

FlushTargetProxy::FlushTargetProxy(const IFlushTarget::SP &target,
                                   const std::string & prefix)
    : IFlushTarget(prefix + "." + target->getName(), target->getType(),
                   target->getComponent()),
      _target(target)
{
}

FlushTargetProxy::~FlushTargetProxy() = default;

IFlushTarget::Task::UP
FlushTargetProxy::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token)
{
    return _target->initFlush(currentSerial, std::move(flush_token));
}

uint64_t
FlushTargetProxy::get_approx_bytes_to_read_from_disk() const noexcept
{
    return _target->get_approx_bytes_to_read_from_disk();
}

std::chrono::steady_clock::duration
FlushTargetProxy::last_flush_duration() const noexcept
{
    return _target->last_flush_duration();
}

} // namespace proton
