// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cmdq.h"
#include "config-owner.h"
#include "metrics.h"
#include "model-subscriber.h"
#include "rpcserver.h"
#include "state-api.h"
#include <vespa/vespalib/net/state_server.h>

namespace config::sentinel {

/**
 * Environment for config sentinel, with config
 * subscription, rpc server, state server, and
 * metrics.
 **/
class Env {
public:
    Env();
    ~Env();

    ConfigOwner &configOwner() { return _cfgOwner; }
    ModelSubscriber &modelSubscriber() { return _modelSubscriber; }
    CommandQueue &commandQueue() { return _rpcCommandQueue; }
    StartMetrics &metrics() { return _startMetrics; }

    void boot(const std::string &configId);
    void rpcPort(int portnum);
    void statePort(int portnum);

    void notifyConfigUpdated();
private:
    void respondAsEmpty();
    ConfigOwner _cfgOwner;
    ModelSubscriber _modelSubscriber;
    CommandQueue _rpcCommandQueue;
    std::unique_ptr<RpcServer> _rpcServer;
    StateApi _stateApi;
    StartMetrics _startMetrics;
    std::unique_ptr<vespalib::StateServer> _stateServer;
    int _statePort;
};

}
