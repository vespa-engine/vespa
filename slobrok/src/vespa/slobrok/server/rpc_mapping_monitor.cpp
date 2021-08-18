// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_mapping_monitor.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.rpc_mapping_monitor");

namespace slobrok {

void RpcMappingMonitor::DelayedTasks::PerformTask() {
    std::vector<MUP> deleteAfterSwap;
    std::swap(deleteAfterSwap, _deleteList);
}

RpcMappingMonitor::RpcMappingMonitor(FRT_Supervisor &orb)
  : _orb(orb),
    _delayedTasks(orb.GetScheduler()),
    _map(),
    _target(nullptr)
{}

RpcMappingMonitor::~RpcMappingMonitor() = default;

void RpcMappingMonitor::target(MappingMonitorListener *listener) {
    if (listener == nullptr) {
        LOG_ASSERT(_target != nullptr);
    } else {
        LOG_ASSERT(_target == nullptr);
    }
    _target = listener;
    LOG(debug, "new target %p", _target);
}

void RpcMappingMonitor::start(const ServiceMapping& mapping) {
    LOG(spam, "start %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    LOG_ASSERT(_map.find(mapping) == _map.end());
    _map.emplace(mapping,
                 std::make_unique<ManagedRpcServer>(mapping.name, mapping.spec, *this));
}

void RpcMappingMonitor::stop(const ServiceMapping& mapping) {
    LOG(spam, "stop %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    auto iter = _map.find(mapping);
    LOG_ASSERT(iter != _map.end());
    _delayedTasks.deleteLater(std::move(iter->second));
    _map.erase(iter);
}

void RpcMappingMonitor::notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) {
    ServiceMapping mapping{rpcsrv->getName(), rpcsrv->getSpec()};
    LOG(spam, "notifyFailed %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    Map::const_iterator iter = _map.find(mapping);
    if (iter == _map.end()) {
        return;
    }
    const MUP & managedRpcServer = iter->second;
    if (managedRpcServer.get() != rpcsrv) {
        return;
    }
    if (_target) {
        LOG(debug, "service %s [at %s] failed: %s",
            mapping.name.c_str(), mapping.spec.c_str(), errmsg.c_str());
        _target->down(mapping);
    }
}

void RpcMappingMonitor::notifyOkRpcSrv(ManagedRpcServer *rpcsrv) {
    ServiceMapping mapping{rpcsrv->getName(), rpcsrv->getSpec()};
    LOG(spam, "notifyOk %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    Map::const_iterator iter = _map.find(mapping);
    if (iter == _map.end()) {
        return;
    }
    const MUP & managedRpcServer = iter->second;
    if (managedRpcServer.get() != rpcsrv) {
        return;
    }
    if (_target) {
        LOG(debug, "service %s [at %s] up ok -> target",
            mapping.name.c_str(), mapping.spec.c_str());
        _target->up(mapping);
    }
}

} // namespace slobrok
