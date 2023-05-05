// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

IFlushTarget::Task::UP
FlushTargetProxy::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token)
{
    return _target->initFlush(currentSerial, std::move(flush_token));
}

} // namespace proton
