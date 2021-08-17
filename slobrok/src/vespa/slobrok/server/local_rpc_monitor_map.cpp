// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "local_rpc_monitor_map.h"
#include "sbenv.h"
#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.local_rpc_monitor_map");

namespace slobrok {

#pragma GCC diagnostic ignored "-Winline"

LocalRpcMonitorMap::LocalRpcMonitorMap(FRT_Supervisor &supervisor)
  : _map(),
    _dispatcher(),
    _history(),
    _supervisor(supervisor),
    _subscription(MapSubscription::subscribe(_dispatcher, _history))
{
}

LocalRpcMonitorMap::~LocalRpcMonitorMap() = default;

LocalRpcMonitorMap::PerService &
LocalRpcMonitorMap::lookup(ManagedRpcServer *rpcsrv) {
    auto iter = _map.find(rpcsrv->getName());
    LOG_ASSERT(iter != _map.end());
    PerService & psd = iter->second;
    LOG_ASSERT(psd.srv.get() == rpcsrv);
    return psd;
}

ServiceMapHistory & LocalRpcMonitorMap::history() {
    return _history;
}

void LocalRpcMonitorMap::addLocal(const ServiceMapping &mapping,
                                  std::unique_ptr<ScriptCommand> inflight)
{
    LOG(debug, "try local add: mapping %s->%s",
        mapping.name.c_str(), mapping.spec.c_str());
    auto old = _map.find(mapping.name);
    if (old != _map.end()) {
        const PerService & exists = old->second;
        if (exists.spec() == mapping.spec) {
            LOG(debug, "added mapping %s->%s was already present",
                mapping.name.c_str(), mapping.spec.c_str());
            inflight->doneHandler(OkState(0, "already registered"));
            return;
        }
        LOG(warning, "tried addLocal for mapping %s->%s, but already had conflicting mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str(),
            exists.name().c_str(), exists.spec().c_str());
        inflight->doneHandler(OkState(FRTE_RPC_METHOD_FAILED, "conflict"));
        return;
    }
    auto [ iter, was_inserted ] =
        _map.try_emplace(mapping.name, localService(mapping, std::move(inflight)));
    LOG_ASSERT(was_inserted);
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
            exists.localOnly = false;
            return;
        }
        PerService removed = std::move(exists);
        _map.erase(old);
        LOG(warning, "added mapping %s->%s, but already had conflicting mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str(),
            removed.name().c_str(), removed.spec().c_str());
        if (removed.inflight) {
            auto target = std::move(removed.inflight);
            target->doneHandler(OkState(13, "conflict during initialization"));
        }
        if (removed.up) {
            _dispatcher.remove(removed.mapping());
        }
    }
    auto [ iter, was_inserted ] =
        _map.try_emplace(mapping.name, globalService(mapping));
    LOG_ASSERT(was_inserted);
}

void LocalRpcMonitorMap::remove(const ServiceMapping &mapping) {
    auto iter = _map.find(mapping.name);
    if (iter != _map.end()) {
        PerService removed = std::move(iter->second);
        _map.erase(iter);
        LOG(debug, "remove: mapping %s->%s", mapping.name.c_str(), mapping.spec.c_str());
        if (mapping.spec != removed.spec()) {
            LOG(warning, "inconsistent specs for name '%s': had '%s', but was asked to remove '%s'",
                mapping.name.c_str(),
                removed.spec().c_str(),
                mapping.spec.c_str());
            return;
        }
        if (removed.inflight) {
            auto target = std::move(removed.inflight);
            target->doneHandler(OkState(13, "removed during initialization"));
        }
        if (removed.up) {
            _dispatcher.remove(removed.mapping());
        }
    } else {
        LOG(debug, "tried to remove non-existing mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str());
    }
}

void LocalRpcMonitorMap::notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string) {
    auto &psd = lookup(rpcsrv);
    LOG(debug, "failed: %s->%s", psd.name().c_str(), psd.spec().c_str());
    if (psd.inflight) {
        auto target = std::move(psd.inflight);
        target->doneHandler(OkState(13, "failed check using listNames callback"));
    }
    if (psd.localOnly) {
        auto iter = _map.find(psd.name());
        _map.erase(iter);
    }
    if (psd.up) {
        psd.up = false;
        _dispatcher.remove(psd.mapping());
    }
}

void LocalRpcMonitorMap::notifyOkRpcSrv(ManagedRpcServer *rpcsrv) {
    auto &psd = lookup(rpcsrv);
    LOG(debug, "ok: %s->%s", psd.name().c_str(), psd.spec().c_str());
    if (psd.inflight) {
        auto target = std::move(psd.inflight);
        target->doneHandler(OkState());
    }
    if (! psd.up) {
        psd.up = true;
        _dispatcher.add(psd.mapping());
    }
}

FRT_Supervisor * LocalRpcMonitorMap::getSupervisor() {
    return &_supervisor;
}

} // namespace slobrok
