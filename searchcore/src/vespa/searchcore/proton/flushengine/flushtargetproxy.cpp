// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
                                   const vespalib::string & prefix)
    : IFlushTarget(prefix + "." + target->getName(), target->getType(),
                   target->getComponent()),
      _target(target)
{
}


IFlushTarget::MemoryGain
FlushTargetProxy::getApproxMemoryGain() const
{
    return _target->getApproxMemoryGain();
}


IFlushTarget::DiskGain
FlushTargetProxy::getApproxDiskGain() const
{
    return _target->getApproxDiskGain();
}


IFlushTarget::SerialNum
FlushTargetProxy::getFlushedSerialNum() const
{
    return _target->getFlushedSerialNum();
}


IFlushTarget::Time
FlushTargetProxy::getLastFlushTime() const
{
    return _target->getLastFlushTime();
}


bool
FlushTargetProxy::needUrgentFlush() const
{
    return _target->needUrgentFlush();
}


IFlushTarget::Task::UP
FlushTargetProxy::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token)
{
    return _target->initFlush(currentSerial, std::move(flush_token));
}


FlushStats
FlushTargetProxy::getLastFlushStats() const
{
    return _target->getLastFlushStats();
}


uint64_t
FlushTargetProxy::getApproxBytesToWriteToDisk() const
{
    return _target->getApproxBytesToWriteToDisk();
}


} // namespace proton
