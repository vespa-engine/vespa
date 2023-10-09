// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/task.h>

namespace slobrok {

class SBEnv;
class RpcServerMap;
class RpcServerManager;
class ExchangeManager;

/**
 * @class RemoteCheck
 * @brief Periodic healthcheck task for remote objects
 *
 * Checks the health of partner location brokers
 * and their NamedService objects periodically.
 **/
class RemoteCheck : public FNET_Task
{
private:
    ExchangeManager &_exchanger;

    RemoteCheck(const RemoteCheck &);            // Not used
    RemoteCheck &operator=(const RemoteCheck &); // Not used
public:
    explicit RemoteCheck(FNET_Scheduler *sched, ExchangeManager& exchanger);
    ~RemoteCheck();
private:
    void PerformTask() override;
};

} // namespace slobrok

