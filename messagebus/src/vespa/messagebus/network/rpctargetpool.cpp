// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".rpctargetpool");

#include <vespa/messagebus/systemtimer.h>
#include "rpctargetpool.h"

namespace mbus {

RPCTargetPool::Entry::Entry(RPCTarget::SP target, uint64_t lastUse) :
    _target(target),
    _lastUse(lastUse)
{
    // empty
}

RPCTargetPool::RPCTargetPool(double expireSecs) :
    _lock(),
    _targets(),
    _timer(new SystemTimer()),
    _expireMillis(static_cast<uint64_t>(expireSecs * 1000))
{
    // empty
}

RPCTargetPool::RPCTargetPool(ITimer::UP timer, double expireSecs) :
    _lock(),
    _targets(),
    _timer(std::move(timer)),
    _expireMillis(static_cast<uint64_t>(expireSecs * 1000))
{
    // empty
}

RPCTargetPool::~RPCTargetPool()
{
    flushTargets(true);
}

void
RPCTargetPool::flushTargets(bool force)
{
    uint64_t currentTime = _timer->getMilliTime();
    vespalib::LockGuard guard(_lock);
    TargetMap::iterator it = _targets.begin();
    while (it != _targets.end()) {
        Entry &entry = it->second;
        if (entry._target.get() != NULL) {
            if (entry._target.use_count() > 1) {
                entry._lastUse = currentTime;
                ++it;
                continue; // someone is using this
            }
            if (!force) {
                if (--entry._lastUse + _expireMillis > currentTime) {
                    ++it;
                    continue; // not sufficiently idle
                }
            }
        }
        _targets.erase(it++); // postfix increment to move the iterator
    }
}

size_t
RPCTargetPool::size()
{
    vespalib::LockGuard guard(_lock);
    return _targets.size();
}

RPCTarget::SP
RPCTargetPool::getTarget(FRT_Supervisor &orb, const RPCServiceAddress &address)
{
    vespalib::LockGuard guard(_lock);
    string spec = address.getConnectionSpec();
    TargetMap::iterator it = _targets.find(spec);
    if (it != _targets.end()) {
        Entry &entry = it->second;
        if (entry._target->isValid()) {
            entry._lastUse = _timer->getMilliTime();
            return entry._target;
        }
        _targets.erase(it);
    }
    RPCTarget::SP ret(new RPCTarget(spec, orb));
    _targets.insert(TargetMap::value_type(spec, Entry(ret, _timer->getMilliTime())));
    return ret;
}

} // namespace mbus
