// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cmd.h"
#include "i_rpc_server_manager.h"
#include "managed_rpc_server.h"
#include "map_listener.h"
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
    struct PerService {
        bool up;
        bool localOnly;
        std::unique_ptr<ScriptCommand> inflight;
        std::unique_ptr<ManagedRpcServer> srv;

        vespalib::string name() { return srv->getName(); }
        vespalib::string spec() { return srv->getSpec(); }
        ServiceMapping mapping() { return ServiceMapping{srv->getName(), srv->getSpec()}; }
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
    
    PerService &lookup(const ServiceMapping &mapping);
    
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

