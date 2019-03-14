// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "service.h"
#include "metrics.h"
#include "state-api.h"
#include "cmdq.h"
#include "rpcserver.h"
#include <vespa/config-sentinel.h>
#include <vespa/config/config.h>
#include <sys/types.h>
#include <sys/select.h>

#include <list>

using cloud::config::SentinelConfig;
using config::ConfigSubscriber;
using config::ConfigHandle;

namespace config::sentinel {

class CommandConnection;
class OutputConnection;

class ConfigHandler {
private:
    typedef std::map<vespalib::string, Service::UP> ServiceMap;

    ConfigSubscriber _subscriber;
    ConfigHandle<SentinelConfig>::UP _sentinelHandle;
    ServiceMap _services;
    std::list<CommandConnection *> _connections;
    std::list<OutputConnection *> _outputConnections;
    CommandQueue _cmdQ;
    std::unique_ptr<RpcServer> _rpcServer;
    int _boundPort;
    int _commandSocket;
    StartMetrics _startMetrics;
    StateApi _stateApi;

    ConfigHandler(const ConfigHandler&);
    ConfigHandler& operator =(const ConfigHandler&);

    Service *serviceByPid(pid_t pid);
    Service *serviceByName(const vespalib::string & name);
    void handleCommands();
    void handleCommand(CommandConnection *c);
    void handleCmd(const Cmd& cmd);
    void handleOutputs();
    void handleChildDeaths();

    static int listen(int port);
    void configure_port(int port);

    void updateMetrics();

    void doGet(CommandConnection *c, char *args);
    void doLs(CommandConnection *c, char *args);
    void doRestart(CommandConnection *c, char *args);
    void doRestart(CommandConnection *c, char *args, bool force);
    void doStart(CommandConnection *c, char *args);
    void doStop(CommandConnection *c, char *args);
    void doStop(CommandConnection *c, char *args, bool force);
    void doAuto(CommandConnection *c, char *args);
    void doManual(CommandConnection *c, char *args);
    void doQuit(CommandConnection *c, char *args);

    void terminateServices(bool catchable, bool printDebug = false);

    void doConfigure();

public:
    ConfigHandler();
    virtual ~ConfigHandler();
    void subscribe(const std::string & configId, uint64_t timeoutMS);
    bool terminate();
    int doWork();
    void updateActiveFdset(fd_set *fds, int *maxNum);
};

}
