// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cmdq.h"
#include "env.h"
#include "metrics.h"
#include "rpcserver.h"
#include "service.h"
#include "state-api.h"
#include <vespa/config-sentinel.h>
#include <vespa/vespalib/net/http/state_server.h>
#include <sys/types.h>
#include <sys/select.h>

#include <list>

using cloud::config::SentinelConfig;
using config::ConfigSubscriber;
using config::ConfigHandle;

namespace config::sentinel {

class OutputConnection;

/**
 *  Management of services.
 *  Handles requests from RPC, service events,
 *  and service configuration updates.
 **/
class Manager {
private:
    typedef std::map<vespalib::string, Service::UP> ServiceMap;

    Env &_env;
    ServiceMap _services;
    ServiceMap _orphans;
    std::list<OutputConnection *> _outputConnections;

    Manager(const Manager&) = delete;
    Manager& operator =(const Manager&) = delete;

    Service *serviceByPid(pid_t pid);
    Service *serviceByName(const vespalib::string & name);
    void handleCommands();
    void handleCmd(const Cmd& cmd);
    void handleOutputs();
    void handleChildDeaths();
    void handleRestarts();

    void updateMetrics();
    void terminateServices(bool catchable, bool printDebug = false);
    void doConfigure();
public:
    Manager(Env &env);
    virtual ~Manager();
    bool terminate();
    bool doWork();
    void updateActiveFdset(fd_set *fds, int *maxNum);
};

}
