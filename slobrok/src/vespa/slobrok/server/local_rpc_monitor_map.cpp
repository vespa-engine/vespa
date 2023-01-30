// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "local_rpc_monitor_map.h"
#include "sbenv.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.local_rpc_monitor_map");

namespace slobrok {

namespace {

struct ChainedCompletionHandler : CompletionHandler {
    std::unique_ptr<CompletionHandler> first;
    std::unique_ptr<CompletionHandler> second;

    ChainedCompletionHandler(std::unique_ptr<CompletionHandler> f,
                                     std::unique_ptr<CompletionHandler> s)
        : first(std::move(f)), second(std::move(s))
    {}

    void doneHandler(OkState result) override {
        first->doneHandler(result);
        second->doneHandler(result);
    }
    ~ChainedCompletionHandler() override;
};

ChainedCompletionHandler::~ChainedCompletionHandler() = default;

}

void LocalRpcMonitorMap::DelayedTasks::PerformTask() {
    std::vector<Event> todo;
    std::swap(todo, _queue);
    for (const auto & entry : todo) {
        switch (entry.type) {
        case EventType::ADD:
            _target.doAdd(entry.mapping);
            break;
        case EventType::REMOVE:
            _target.doRemove(entry.mapping);
            break;
        }
    }
}

LocalRpcMonitorMap::PerService::~PerService() = default;
LocalRpcMonitorMap::PerService::PerService(PerService &&) noexcept = default;
LocalRpcMonitorMap::PerService & LocalRpcMonitorMap::PerService::operator =(PerService &&) noexcept = default;

LocalRpcMonitorMap::RemovedData::~RemovedData() = default;

LocalRpcMonitorMap::LocalRpcMonitorMap(FNET_Scheduler *scheduler,
                                       MappingMonitorFactory mappingMonitorFactory)
  : _delayedTasks(scheduler, *this),
    _map(),
    _dispatcher(),
    _history(),
    _mappingMonitor(mappingMonitorFactory(*this)),
    _subscription(MapSubscription::subscribe(_dispatcher, _history))
{
}

LocalRpcMonitorMap::~LocalRpcMonitorMap() = default;

LocalRpcMonitorMap::PerService &
LocalRpcMonitorMap::lookup(const ServiceMapping &mapping) {
    LOG(spam, "lookup %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    auto iter = _map.find(mapping.name);
    if (iter == _map.end()) {
        LOG_ABORT("not in map");
    }
    PerService & psd = iter->second;
    if (psd.spec != mapping.spec) {
        LOG_ABORT("conflict in map: %s->%s");
    }
    LOG(spam, "found in map: %s->%s [%s,%s]",
        iter->first.c_str(), psd.spec.c_str(),
        psd.up ? "up" : "down",
        psd.localOnly ? "local" : "global");
    return psd;
}

void LocalRpcMonitorMap::addToMap(const ServiceMapping &mapping, PerService psd, bool hurry) {
    auto [ iter, was_inserted ] =
        _map.try_emplace(mapping.name, std::move(psd));
    LOG_ASSERT(was_inserted);
    _mappingMonitor->start(mapping, hurry);
}

LocalRpcMonitorMap::RemovedData
LocalRpcMonitorMap::removeFromMap(Map::iterator iter) {
    auto name = iter->first;
    PerService psd = std::move(iter->second);
    ServiceMapping mapping{iter->first, psd.spec};
    _mappingMonitor->stop(mapping);
    _map.erase(iter);
    return RemovedData {
        .mapping = mapping,
        .up = psd.up,
        .localOnly = psd.localOnly,
        .inflight = std::move(psd.inflight)
    };
}

ServiceMapHistory & LocalRpcMonitorMap::history() {
    return _history;
}

bool LocalRpcMonitorMap::wouldConflict(const ServiceMapping &mapping) const {
    auto iter = _map.find(mapping.name);
    if (iter == _map.end()) {
        return false; // no mapping, no conflict
    }
    return (iter->second.spec != mapping.spec);
}

void LocalRpcMonitorMap::addLocal(const ServiceMapping &mapping,
                                  std::unique_ptr<CompletionHandler> inflight)
{
    LOG(debug, "try local add: mapping %s->%s",
        mapping.name.c_str(), mapping.spec.c_str());
    auto old = _map.find(mapping.name);
    if (old != _map.end()) {
        PerService & exists = old->second;
        if (exists.spec == mapping.spec) {
            LOG(debug, "added mapping %s->%s was already present",
                mapping.name.c_str(), mapping.spec.c_str());
            if (exists.up) {
                inflight->doneHandler(OkState(0, "already registered"));
            } else if (exists.inflight) {
                auto newInflight = std::make_unique<ChainedCompletionHandler>(
                    std::move(exists.inflight),
                    std::move(inflight));
                exists.inflight = std::move(newInflight);
            } else {
                _mappingMonitor->stop(mapping);
                exists.inflight = std::move(inflight);
                _mappingMonitor->start(mapping, true);
            }
            return;
        }
        LOG(warning, "tried addLocal for mapping %s->%s, but already had conflicting mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str(),
            mapping.name.c_str(), exists.spec.c_str());
        inflight->doneHandler(OkState(FRTE_RPC_METHOD_FAILED, "conflict"));
        return;
    }
    addToMap(mapping, localService(mapping, std::move(inflight)), true);
}

void LocalRpcMonitorMap::removeLocal(const ServiceMapping &mapping) {
    LOG(debug, "try local remove: mapping %s->%s",
        mapping.name.c_str(), mapping.spec.c_str());
    auto old = _map.find(mapping.name);
    if (old == _map.end()) {
        return; // already removed, OK
    }
    PerService & exists = old->second;
    if (exists.spec != mapping.spec) {
        LOG(warning, "tried removeLocal for mapping %s->%s, but already had conflicting mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str(),
            mapping.name.c_str(), exists.spec.c_str());
        return; // unregister for old, conflicting mapping
    }
    if (exists.localOnly) {
        // we can just remove it
        auto removed = removeFromMap(old);
        if (removed.inflight) {
            auto target = std::move(removed.inflight);
            target->doneHandler(OkState(13, "removed during initialization"));
        }
        if (removed.up) {
            _dispatcher.remove(removed.mapping);            
        }
        return;
    }
    // also exists in consensus map, so we can't just remove it
    // instead, pretend it's down and delay next ping
    _mappingMonitor->stop(mapping);
    if (exists.up) {
        exists.up = false;
        _dispatcher.remove(mapping);            
    }
    _mappingMonitor->start(mapping, false);
}

void LocalRpcMonitorMap::add(const ServiceMapping &mapping) {
    _delayedTasks.handleLater(Event::add(mapping));
}

void LocalRpcMonitorMap::remove(const ServiceMapping &mapping) {
    _delayedTasks.handleLater(Event::remove(mapping));
}

void LocalRpcMonitorMap::doAdd(const ServiceMapping &mapping) {
    LOG(debug, "try add: mapping %s->%s",
        mapping.name.c_str(), mapping.spec.c_str());
    auto old = _map.find(mapping.name);
    if (old != _map.end()) {
        PerService & exists = old->second;
        if (exists.spec == mapping.spec) {
            LOG(debug, "added mapping %s->%s was already present",
                mapping.name.c_str(), mapping.spec.c_str());
            exists.localOnly = false;
            return;
        }
        auto removed = removeFromMap(old);
        LOG(warning, "added mapping %s->%s, but already had conflicting mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str(),
            removed.mapping.name.c_str(), removed.mapping.spec.c_str());
        if (removed.inflight) {
            auto target = std::move(removed.inflight);
            target->doneHandler(OkState(13, "conflict during initialization"));
        }
        if (removed.up) {
            _dispatcher.remove(removed.mapping);
        }
    }
    addToMap(mapping, globalService(mapping), false);
}

void LocalRpcMonitorMap::doRemove(const ServiceMapping &mapping) {
    auto iter = _map.find(mapping.name);
    if (iter != _map.end()) {
        auto removed = removeFromMap(iter);
        LOG(debug, "remove: mapping %s->%s",
            removed.mapping.name.c_str(), removed.mapping.spec.c_str());
        if (mapping.spec != removed.mapping.spec) {
            LOG(warning, "inconsistent specs for name '%s': had '%s', but was asked to remove '%s'",
                mapping.name.c_str(),
                removed.mapping.spec.c_str(),
                mapping.spec.c_str());
        }
        if (removed.inflight) {
            auto target = std::move(removed.inflight);
            target->doneHandler(OkState(13, "removed during initialization"));
        }
        if (removed.up) {
            _dispatcher.remove(removed.mapping);
        }
    } else {
        LOG(debug, "tried to remove non-existing mapping %s->%s",
            mapping.name.c_str(), mapping.spec.c_str());
    }
}

void LocalRpcMonitorMap::down(const ServiceMapping& mapping) {
    PerService &psd = lookup(mapping);
    LOG(debug, "failed: %s->%s", mapping.name.c_str(), psd.spec.c_str());
    if (psd.inflight) {
        auto target = std::move(psd.inflight);
        target->doneHandler(OkState(13, "failed check using listNames callback"));
    }
    if (psd.localOnly) {
        auto iter = _map.find(mapping.name);
        auto removed = removeFromMap(iter);
        if (removed.up) {
            _dispatcher.remove(removed.mapping);
        }
    } else if (psd.up) {
        psd.up = false;
        _dispatcher.remove(mapping);
    }
}

void LocalRpcMonitorMap::up(const ServiceMapping& mapping) {
    PerService &psd = lookup(mapping);
    LOG(debug, "ok: %s->%s", mapping.name.c_str(), psd.spec.c_str());
    if (psd.inflight) {
        auto target = std::move(psd.inflight);
        target->doneHandler(OkState());
    }
    if (! psd.up) {
        psd.up = true;
        _dispatcher.add(mapping);
    }
}

} // namespace slobrok
