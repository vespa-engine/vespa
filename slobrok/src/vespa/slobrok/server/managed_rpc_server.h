// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "named_service.h"
#include "monitor.h"
#include <vespa/fnet/frt/invoker.h>

namespace slobrok {

//-----------------------------------------------------------------------------

class IRpcServerManager;

/**
 * @class ManagedRpcServer
 * @brief A NamedService that is managed by this location broker
 *
 * This class contains the logic to monitor the connection to a
 * NamedService and also to do a healthCheck using the RPC method
 * slobrok.checkRpcServer on the connection, notifying its
 * manager using callbacks in the IRpcServerManager interface.
 **/

class ManagedRpcServer: public NamedService,
                        public FRT_IRequestWait,
                        public FNET_Task,
                        public IMonitoredServer
{
public:
    ManagedRpcServer(const ManagedRpcServer&) = delete;
    ManagedRpcServer& operator=(const ManagedRpcServer&) = delete;
    ManagedRpcServer(const std::string & name, const std::string & spec, IRpcServerManager &manager);
    ~ManagedRpcServer();

    void healthCheck();
    void PerformTask() override;

private:
    IRpcServerManager      &_mmanager;
    Monitor               _monitor;
    FRT_Target           *_monitoredServer;
    FRT_RPCRequest       *_checkServerReq;

    void cleanupMonitor();
    bool validateRpcServer(uint32_t numstrings,
                           FRT_StringValue *strings);
public:
    void RequestDone(FRT_RPCRequest *req) override;
    void notifyDisconnected() override; // lost connection to service
};

//-----------------------------------------------------------------------------

} // namespace slobrok

