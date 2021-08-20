// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cmd.h"
#include "i_rpc_server_manager.h"
#include "managed_rpc_server.h"
#include "map_listener.h"
#include "map_source.h"
#include "named_service.h"
#include "proxy_map_source.h"
#include "service_map_history.h"
#include "service_mapping.h"

#include <vector>
#include <memory>
#include <map>

namespace slobrok {

/**
 * @class LocalRpcMonitorMap
 * @brief A collection of ManagedRpcServer objects
 *
 * Tracks up/down status for name->spec combinations
 * that are considered for publication locally.
 **/
class LocalRpcMonitorMap : public IRpcServerManager,
                           public MapListener
{
private:
    enum class EventType { ADD, REMOVE };

    struct Event {
        EventType type;
        ServiceMapping mapping;
        static Event add(const ServiceMapping &value) {
            return Event{EventType::ADD, value};
        }
        static Event remove(const ServiceMapping &value) {
            return Event{EventType::REMOVE, value};
        }
    };

    class DelayedTasks : public FNET_Task {
        using MUP = std::unique_ptr<ManagedRpcServer>;
        std::vector<MUP>    _deleteList;
        std::vector<Event>  _queue;
        LocalRpcMonitorMap &_target;
    public:
        void deleteLater(MUP rpcsrv) {
            _deleteList.emplace_back(std::move(rpcsrv));
            ScheduleNow();
        }

        void handleLater(Event event) {
            _queue.emplace_back(std::move(event));
            ScheduleNow();
        }

        void PerformTask() override;

        DelayedTasks(FNET_Scheduler *scheduler, LocalRpcMonitorMap &target)
          : FNET_Task(scheduler),
            _deleteList(),
            _queue(),
            _target(target)
        {}

        ~DelayedTasks() { Kill(); }
    };

    DelayedTasks _delayedTasks;

    struct PerService {
        bool up;
        bool localOnly;
        std::unique_ptr<ScriptCommand> inflight;
        std::unique_ptr<ManagedRpcServer> srv;

        vespalib::string name() const { return srv->getName(); }
        vespalib::string spec() const { return srv->getSpec(); }
        ServiceMapping mapping() const { return ServiceMapping{srv->getName(), srv->getSpec()}; }
    };

    std::unique_ptr<ManagedRpcServer> managedFor(const ServiceMapping &mapping) {
        return std::make_unique<ManagedRpcServer>(mapping.name, mapping.spec, *this);
    }

    PerService localService(const ServiceMapping &mapping,
                            std::unique_ptr<ScriptCommand> inflight)
    {
        return PerService{
            .up = false,
            .localOnly = true,
            .inflight = std::move(inflight),
            .srv = managedFor(mapping)
        };
    }

    PerService globalService(const ServiceMapping &mapping) {
        return PerService{
            .up = false,
            .localOnly = false,
            .inflight = {},
            .srv = managedFor(mapping)
        };
    }        

    using Map = std::map<vespalib::string, PerService>;

    Map _map;
    ProxyMapSource _dispatcher;
    ServiceMapHistory _history;
    FRT_Supervisor &_supervisor;
    std::unique_ptr<MapSubscription> _subscription;
    
    PerService *lookup(ManagedRpcServer *rpcsrv);

    void doAdd(const ServiceMapping &mapping);
    void doRemove(const ServiceMapping &mapping);
    
public:
    LocalRpcMonitorMap(FRT_Supervisor &_supervisor);
    ~LocalRpcMonitorMap();

    MapSource &dispatcher() { return _dispatcher; }
    ServiceMapHistory & history();

    /** for use by register API, will call doneHandler() on inflight script */
    void addLocal(const ServiceMapping &mapping,
                  std::unique_ptr<ScriptCommand> inflight);

    void add(const ServiceMapping &mapping) override;
    void remove(const ServiceMapping &mapping) override;

    void notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) override;
    void notifyOkRpcSrv(ManagedRpcServer *rpcsrv) override;
    FRT_Supervisor *getSupervisor() override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok

