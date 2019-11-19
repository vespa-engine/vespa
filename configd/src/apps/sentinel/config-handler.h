// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "service.h"
#include "metrics.h"
#include "state-api.h"
#include "cmdq.h"
#include "rpcserver.h"
#include <vespa/config-sentinel.h>
#include <vespa/config/config.h>
#include <vespa/vespalib/net/state_server.h>
#include <sys/types.h>
#include <sys/select.h>

#include <list>

using cloud::config::SentinelConfig;
using config::ConfigSubscriber;
using config::ConfigHandle;

namespace config::sentinel {

class OutputConnection;

class ConfigHandler {
private:
    typedef std::map<vespalib::string, Service::UP> ServiceMap;

    ConfigSubscriber _subscriber;
    ConfigHandle<SentinelConfig>::UP _sentinelHandle;
    ServiceMap _services;
    ServiceMap _orphans;
    std::list<OutputConnection *> _outputConnections;
    CommandQueue _cmdQ;
    std::unique_ptr<RpcServer> _rpcServer;
    int _boundPort;
    StartMetrics _startMetrics;
    StateApi _stateApi;
    std::unique_ptr<vespalib::StateServer> _stateServer;

    ConfigHandler(const ConfigHandler&);
    ConfigHandler& operator =(const ConfigHandler&);

    Service *serviceByPid(pid_t pid);
    Service *serviceByName(const vespalib::string & name);
    void handleCommands();
    void handleCmd(const Cmd& cmd);
    void handleOutputs();
    void handleChildDeaths();
    void handleRestarts();

    static int listen(int port);
    void configure_port(int port);

    void updateMetrics();

    void terminateServices(bool catchable, bool printDebug = false);

    void doConfigure();

public:
    ConfigHandler();
    virtual ~ConfigHandler();
    void subscribe(const std::string & configId, std::chrono::milliseconds timeout);
    bool terminate();
    int doWork();
    void updateActiveFdset(fd_set *fds, int *maxNum);
};

}
