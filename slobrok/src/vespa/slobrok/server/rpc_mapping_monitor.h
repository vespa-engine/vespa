// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "mapping_monitor.h"
#include "i_rpc_server_manager.h"
#include "managed_rpc_server.h"

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/task.h>

#include <vector>
#include <memory>
#include <map>

namespace slobrok {

class RpcMappingMonitor : public MappingMonitor,
                          public IRpcServerManager
{
private:
    using MUP = std::unique_ptr<ManagedRpcServer>;

    using Map = std::map<ServiceMapping, MUP>;

    class DelayedTasks : public FNET_Task {
        std::vector<MUP>    _deleteList;
    public:
        void deleteLater(MUP rpcsrv) {
            _deleteList.emplace_back(std::move(rpcsrv));
            ScheduleNow();
        }
        void PerformTask() override;
        DelayedTasks(FNET_Scheduler *scheduler)
          : FNET_Task(scheduler),
            _deleteList()
        {}
        ~DelayedTasks() { Kill(); }
    };

    FRT_Supervisor& _orb;
    DelayedTasks _delayedTasks;
    Map _map;
    MappingMonitorOwner &_owner;

    bool active(const ServiceMapping &mapping, ManagedRpcServer *rpcsrv) const;

public:
    RpcMappingMonitor(FRT_Supervisor &orb, MappingMonitorOwner &owner);
    ~RpcMappingMonitor();

    void start(const ServiceMapping& mapping, bool hurry) override;
    void stop(const ServiceMapping& mapping) override;

    void notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) override;
    void notifyOkRpcSrv(ManagedRpcServer *rpcsrv) override;
    FRT_Supervisor *getSupervisor() override { return &_orb; }
};

} // namespace slobrok

