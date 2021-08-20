// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cmd.h"
#include "managed_rpc_server.h"
#include "map_listener.h"
#include "map_source.h"
#include "mapping_monitor.h"
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
class LocalRpcMonitorMap : public MapListener,
                           public MappingMonitorOwner
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
        std::vector<Event>  _queue;
        LocalRpcMonitorMap &_target;
    public:
        void handleLater(Event event) {
            _queue.emplace_back(std::move(event));
            ScheduleNow();
        }

        void PerformTask() override;

        DelayedTasks(FNET_Scheduler *scheduler, LocalRpcMonitorMap &target)
          : FNET_Task(scheduler),
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
        vespalib::string spec;
    };

    PerService localService(const ServiceMapping &mapping,
                            std::unique_ptr<ScriptCommand> inflight)
    {
        return PerService{
            .up = false,
            .localOnly = true,
            .inflight = std::move(inflight),
            .spec = mapping.spec
        };
    }

    PerService globalService(const ServiceMapping &mapping) {
        return PerService{
            .up = false,
            .localOnly = false,
            .inflight = {},
            .spec = mapping.spec
        };
    }        

    using Map = std::map<vespalib::string, PerService>;

    Map _map;
    ProxyMapSource _dispatcher;
    ServiceMapHistory _history;
    MappingMonitor::UP _mappingMonitor;
    std::unique_ptr<MapSubscription> _subscription;
    
    void doAdd(const ServiceMapping &mapping);
    void doRemove(const ServiceMapping &mapping);

    PerService & lookup(const ServiceMapping &mapping);

    void addToMap(const ServiceMapping &mapping, PerService psd);

    struct RemovedData {
        ServiceMapping mapping;
        bool up;
        bool localOnly;
        std::unique_ptr<ScriptCommand> inflight;
    };

    RemovedData removeFromMap(Map::iterator iter);

public:
    LocalRpcMonitorMap(FRT_Supervisor &supervisor,
                       MappingMonitorFactory mappingMonitorFactory);
    ~LocalRpcMonitorMap();

    MapSource &dispatcher() { return _dispatcher; }
    ServiceMapHistory & history();

    /** for use by register API, will call doneHandler() on inflight script */
    void addLocal(const ServiceMapping &mapping,
                  std::unique_ptr<ScriptCommand> inflight);

    void add(const ServiceMapping &mapping) override;
    void remove(const ServiceMapping &mapping) override;

    void up(const ServiceMapping& mapping) override;
    void down(const ServiceMapping& mapping) override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok

