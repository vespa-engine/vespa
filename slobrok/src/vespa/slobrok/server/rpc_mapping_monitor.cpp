// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_mapping_monitor.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.rpc_mapping_monitor");

namespace slobrok {

void RpcMappingMonitor::DelayedTasks::PerformTask() {
    std::vector<MUP> deleteAfterSwap;
    std::swap(deleteAfterSwap, _deleteList);
}

RpcMappingMonitor::RpcMappingMonitor(FRT_Supervisor &orb, MappingMonitorOwner &owner)
  : _orb(orb),
    _delayedTasks(orb.GetScheduler()),
    _map(),
    _owner(owner)
{}

RpcMappingMonitor::~RpcMappingMonitor() = default;

void RpcMappingMonitor::start(const ServiceMapping& mapping, bool hurry) {
    LOG(spam, "start %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    LOG_ASSERT(_map.find(mapping) == _map.end());
    auto up = std::make_unique<ManagedRpcServer>(mapping.name, mapping.spec, *this);
    auto & managed = *up;
    _map.emplace(mapping, std::move(up));
    if (hurry) {
        managed.healthCheck();
    }
}

void RpcMappingMonitor::stop(const ServiceMapping& mapping) {
    LOG(spam, "stop %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    auto iter = _map.find(mapping);
    LOG_ASSERT(iter != _map.end());
    _delayedTasks.deleteLater(std::move(iter->second));
    _map.erase(iter);
}


bool RpcMappingMonitor::active(const ServiceMapping &mapping, ManagedRpcServer *rpcsrv) const {
    auto iter = _map.find(mapping);
    if (iter == _map.end()) {
        return false;
    }
    return iter->second.get() == rpcsrv;
}
        
void RpcMappingMonitor::notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) {
    ServiceMapping mapping{rpcsrv->getName(), rpcsrv->getSpec()};
    LOG(spam, "notifyFailed %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    if (active(mapping, rpcsrv)) {
        LOG(debug, "service %s [at %s] failed: %s",
            mapping.name.c_str(), mapping.spec.c_str(), errmsg.c_str());
        _owner.down(mapping);
    }
}

void RpcMappingMonitor::notifyOkRpcSrv(ManagedRpcServer *rpcsrv) {
    ServiceMapping mapping{rpcsrv->getName(), rpcsrv->getSpec()};
    LOG(spam, "notifyOk %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    if (active(mapping, rpcsrv)) {
        LOG(debug, "service %s [at %s] up ok -> target",
            mapping.name.c_str(), mapping.spec.c_str());
        _owner.up(mapping);
    }
}

} // namespace slobrok
