// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "local_rpc_monitor_map.h"
#include "sbenv.h"
#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.local_rpc_monitor_map");

namespace slobrok {

LocalRpcMonitorMap::LocalRpcMonitorMap(FRT_Supervisor &supervisor)
  : _map(),
    _history(),
    _supervisor(supervisor)
{
}

LocalRpcMonitorMap::~LocalRpcMonitorMap() = default;

LocalRpcMonitorMap::PerService *
LocalRpcMonitorMap::lookup(const ServiceMapping &mapping) {
    auto iter = _map.find(mapping.name);
    if (iter != _map.end()) {
        PerService & psd = iter->second;
        LOG_ASSERT(psd.spec() == mapping.spec);
        return &psd;
    }
    return nullptr;
}

ServiceMapHistory & LocalRpcMonitorMap::history() {
    return _history;
}

void LocalRpcMonitorMap::add(const ServiceMapping &mapping) {
    LOG(debug, "try add: mapping %s->%s",
        mapping.name.c_str(), mapping.spec.c_str());
    auto old = _map.find(mapping.name);
    if (old != _map.end()) {
        PerService & exists = old->second;
        if (exists.spec() == mapping.spec) {
            LOG(debug, "added mapping %s->%s was already present",
                mapping.name.c_str(), mapping.spec.c_str());
            return;
        }
        LOG(warning, "added mapping %s->%s, but already had conflicting mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str(),
            exists.name().c_str(), exists.spec().c_str());
        if (exists.up) {
            _history.remove(exists.mapping());
        }
        _map.erase(old);
    }
    auto [ iter, was_inserted ] =
        _map.try_emplace(mapping.name,
                         false,
                         std::make_unique<ManagedRpcServer>(mapping.name,
                                                            mapping.spec,
                                                            *this));
    LOG_ASSERT(was_inserted);
}

void LocalRpcMonitorMap::remove(const ServiceMapping &mapping) {
    auto iter = _map.find(mapping.name);
    if (iter != _map.end()) {
        LOG(debug, "remove: mapping %s->%s", mapping.name.c_str(), mapping.spec.c_str());
        PerService & exists = iter->second;
        if (mapping.spec != exists.spec()) {
            LOG(warning, "inconsistent specs for name '%s': had '%s', but was asked to remove '%s'",
                mapping.name.c_str(),
                exists.spec().c_str(),
                mapping.spec.c_str());
        }
        if (exists.up) {
            _history.remove(exists.mapping());
        }
        _map.erase(iter);
    } else {
        LOG(debug, "tried to remove non-existing mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str());
    }
}

void LocalRpcMonitorMap::notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string) {
    ServiceMapping mapping{rpcsrv->getName(), rpcsrv->getSpec()};
    auto * psd = lookup(mapping);
    LOG_ASSERT(psd);
    LOG_ASSERT(psd->srv.get() == rpcsrv);
    LOG(debug, "failed: %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    if (psd->up) {
        psd->up = false;
        _history.remove(mapping);
    }
}

void LocalRpcMonitorMap::notifyOkRpcSrv(ManagedRpcServer *rpcsrv) {
    ServiceMapping mapping{rpcsrv->getName(), rpcsrv->getSpec()};
    auto * psd = lookup(mapping);
    LOG_ASSERT(psd);
    LOG_ASSERT(psd->srv.get() == rpcsrv);
    LOG(debug, "ok: %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    if (! psd->up) {
        psd->up = true;
        _history.add(mapping);
    }
}

FRT_Supervisor * LocalRpcMonitorMap::getSupervisor() {
    return &_supervisor;
}

} // namespace slobrok
