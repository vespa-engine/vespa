// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/task.h>

namespace slobrok {

class SBEnv;
class RpcServerMap;
class RpcServerManager;
class ExchangeManager;

/**
 * @class SelfCheck
 * @brief Periodic healthcheck task
 *
 * Checks the health of this location broker
 * and its ManagedRpcServer objects periodically.
 **/
class SelfCheck : public FNET_Task
{
private:
    RpcServerMap &_rpcsrvmap;
    RpcServerManager &_rpcsrvmanager;

    SelfCheck(const SelfCheck &);            // Not used
    SelfCheck &operator=(const SelfCheck &); // Not used
public:
    explicit SelfCheck(FNET_Scheduler *sched,
                       RpcServerMap& rpcsrvmap,
                       RpcServerManager& rpcsrvman);
    ~SelfCheck();
private:
    void PerformTask() override;
};

} // namespace slobrok

