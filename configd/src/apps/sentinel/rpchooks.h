// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "model-owner.h"
#include <vespa/fnet/frt/invokable.h>
#include <memory>

class FNET_Task;
class FRT_Supervisor;

namespace config::sentinel {

class CommandQueue;

/**
 * @class RPCHooks
 * @brief The FNET-RPC interface to a config sentinel
 *
 * Contains methods for receiveing and unpacking requests,
 * invoking the right internal method, and (in most cases)
 * packaging and returning the result of the request.
 **/
class RPCHooks : public FRT_Invokable
{
private:
    CommandQueue &_commands;
    FRT_Supervisor &_orb;
    ModelOwner &_modelOwner;
public:
    RPCHooks(CommandQueue &commands, FRT_Supervisor &supervisor, ModelOwner &modelOwner);
    ~RPCHooks() override;
private:
    void initRPC(FRT_Supervisor *supervisor);

    void rpc_listServices(FRT_RPCRequest *req);
    void rpc_restartService(FRT_RPCRequest *req);
    void rpc_stopService(FRT_RPCRequest *req);
    void rpc_startService(FRT_RPCRequest *req);
    void rpc_checkConnectivity(FRT_RPCRequest *req);
    void rpc_reportConnectivity(FRT_RPCRequest *req);
};

} // namespace config::sentinel
