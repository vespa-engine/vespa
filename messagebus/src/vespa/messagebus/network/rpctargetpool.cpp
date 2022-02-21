// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpctargetpool.h"
#include <vespa/messagebus/steadytimer.h>

namespace mbus {

RPCTargetPool::Entry::Entry(std::vector<RPCTarget::SP> targets, uint64_t lastUse)
    : _targets(std::move(targets)),
      _lastUse(lastUse),
      _next(0)
{ }

RPCTarget::SP
RPCTargetPool::Entry::getTarget(const LockGuard &, uint64_t now) {
    if (_next >= _targets.size()) {
        _next = 0;
    }
    RPCTarget::SP target =  _targets[_next++];
    if ( ! target->isValid()) {
        return RPCTarget::SP();
    }
    _lastUse = now;
    return target;
}

bool
RPCTargetPool::Entry::inUse(const LockGuard &) const {
    for (const auto & target : _targets) {
        if (target.use_count() > 1) {
            return true;
        }
    }
    return false;
}

RPCTargetPool::RPCTargetPool(double expireSecs, size_t numTargetsPerSpec)
    : RPCTargetPool(std::make_unique<SteadyTimer>(), expireSecs, numTargetsPerSpec)
{ }

RPCTargetPool::RPCTargetPool(ITimer::UP timer, double expireSecs, size_t numTargetsPerSpec) :
    _lock(),
    _targets(),
    _timer(std::move(timer)),
    _expireMillis(static_cast<uint64_t>(expireSecs * 1000)),
    _numTargetsPerSpec(numTargetsPerSpec)
{ }

RPCTargetPool::~RPCTargetPool()
{
    flushTargets(true);
}

void
RPCTargetPool::flushTargets(bool force)
{
    uint64_t currentTime = _timer->getMilliTime();
    // Erase RPC targets outside our lock to prevent the following mutex order inversion potential:
    //   flushTargets (pool lock) -> FNET transport thread post event (transport thread lock)
    //   FNET CheckTasks (transport thread lock) -> periodic flushTargets task run -> flushTargets (pool lock)
    std::vector<Entry> to_erase_on_scope_exit;
    LockGuard guard(_lock);
    {
        auto it = _targets.begin();
        while (it != _targets.end()) {
            const Entry& entry = it->second;
            if (!entry.inUse(guard) && (force || ((entry.lastUse() + _expireMillis) < currentTime))) {
                to_erase_on_scope_exit.emplace_back(std::move(it->second));
                it = _targets.erase(it);
            } else {
                ++it;
            }
        }
    }
}

size_t
RPCTargetPool::size()
{
    LockGuard guard(_lock);
    return _targets.size();
}

RPCTarget::SP
RPCTargetPool::getTarget(FRT_Supervisor &orb, const RPCServiceAddress &address)
{
    const string & spec = address.getConnectionSpec();
    uint64_t currentTime = _timer->getMilliTime();
    LockGuard guard(_lock);
    auto it = _targets.find(spec);
    if (it != _targets.end()) {
        RPCTarget::SP target = it->second.getTarget(guard, currentTime);
        if (target) {
            return target;
        }
        _targets.erase(it);
    }
    std::vector<RPCTarget::SP> targets;
    targets.reserve(_numTargetsPerSpec);
    for (size_t i(0); i < _numTargetsPerSpec; i++) {
        targets.push_back(std::make_shared<RPCTarget>(spec, orb));
    }
    _targets.insert(TargetMap::value_type(spec, Entry(std::move(targets), currentTime)));
    return _targets.find(spec)->second.getTarget(guard, currentTime);
}

} // namespace mbus
