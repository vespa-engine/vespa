// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-handler.h"
#include "command-connection.h"
#include "output-connection.h"

#include <vespa/vespalib/net/simple_metric_snapshot.h>
#include <vespa/vespalib/net/socket_address.h>
#include <fcntl.h>
#include <sys/wait.h>

#include <vespa/log/log.h>
LOG_SETUP(".config-handler");

namespace config::sentinel {

int
ConfigHandler::listen(int port) {
    auto handle = vespalib::SocketAddress::select_local(port).listen();
    if (!handle) {
        LOG(error, "Fatal: listen on command control socket failed: %s", strerror(errno));
        EV_STOPPING("config-sentinel", "listen on command control socket failed");
        exit(EXIT_FAILURE);
    }
    int fd = handle.release();
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
    fcntl(fd, F_SETFD, FD_CLOEXEC);
    return fd;
}

void
ConfigHandler::configure_port(int port)
{
    if (port == 0) {
        port = 19098;
        const char *portString = getenv("VESPA_SENTINEL_PORT");
        if (portString) {
            port = strtoul(portString, nullptr, 10);
        }
    }
    if (port <= 0 || port > 65535) {
        LOG(error, "Fatal: bad port %d, expected range [1,65535]", port);
        EV_STOPPING("config-sentinel", "bad port");
        exit(EXIT_FAILURE);
    }
    LOG(debug, "Config-sentinel accepts connections on port %d", port);
    close(_commandSocket);
    _commandSocket = listen(port);
    _boundPort = port;
}

ConfigHandler::ConfigHandler()
    : _subscriber(),
      _services(),
      _connections(),
      _outputConnections(),
      _boundPort(0),
      _commandSocket(listen(0)),
      _startMetrics(),
      _stateApi(_startMetrics.producer)
{
    _startMetrics.startedTime = time(nullptr);
}

ConfigHandler::~ConfigHandler()
{
    terminateServices(false);
    std::list<CommandConnection *>::iterator i;
    for (i = _connections.begin(); i != _connections.end(); ++i)
    {
        delete *i;
    }
    std::list<OutputConnection *>::iterator it;
    for (it = _outputConnections.begin(); it != _outputConnections.end(); ++it)
    {
        delete *it;
    }
    close(_commandSocket);
}

void
ConfigHandler::terminateServices(bool catchable, bool printDebug)
{
    for (ServiceMap::iterator it(_services.begin()), mt(_services.end()); it != mt; it++) {
        Service *service = it->second.get();
        if (printDebug && service->isRunning()) {
            LOG(info, "%s: killing", service->name().c_str());
        }
        service->terminate(catchable, printDebug);
    }
}


bool
ConfigHandler::terminate()
{
    // Call terminate(true) for all services.
    // Give them 58 seconds to exit cleanly, then terminate(false) all
    // of them.
    terminateServices(true);
    struct timeval endTime;
    gettimeofday(&endTime, nullptr);
    endTime.tv_sec += 58;
    struct timeval tv = {0, 0};

    while (tv.tv_sec >= 0 && doWork()) {
        gettimeofday(&tv, nullptr);
        tv.tv_sec = endTime.tv_sec - tv.tv_sec;
        tv.tv_usec = endTime.tv_usec - tv.tv_usec;

        if (tv.tv_usec >= 1000000) {
            tv.tv_usec -= 1000000;
            tv.tv_sec += 1;
        } else if (tv.tv_usec < 0) {
            tv.tv_usec += 100000;
            tv.tv_sec -= 1;
        }

        if (tv.tv_sec < 0) {
            break;
        }

        if (tv.tv_sec > 0 || tv.tv_usec > 200000) {
            // Never wait more than 200ms per select regardless
            tv.tv_sec = 0;
            tv.tv_usec = 200000;
        }

        // Any child exiting will send SIGCHLD and break this select so
        // we handle the children exiting even quicker..
        select(0, nullptr, nullptr, nullptr, &tv);
    }
    for (int retry = 0; retry < 10 && doWork(); ++retry) {
        LOG(warning, "some services refuse to terminate cleanly, sending KILL");
        terminateServices(false, true);
        tv.tv_sec = 0;
        tv.tv_usec = 200000;
        select(0, nullptr, nullptr, nullptr, &tv);
    }
    return !doWork();
}

void
ConfigHandler::subscribe(const std::string & configId, uint64_t timeoutMS)
{
    _sentinelHandle = _subscriber.subscribe<SentinelConfig>(configId, timeoutMS);
}

void
ConfigHandler::doConfigure()
{
    std::unique_ptr<SentinelConfig> cfg(_sentinelHandle->getConfig());
    const SentinelConfig& config(*cfg);

    if (config.port.telnet != _boundPort) {
        configure_port(config.port.telnet);
        _stateApi.bound(_boundPort);
    }

    if (!_rpcServer || config.port.rpc != _rpcServer->getPort()) {
        _rpcServer = std::make_unique<RpcServer>(config.port.rpc, _cmdQ);
    }

    LOG(debug, "ConfigHandler::configure() %d config elements, tenant(%s), application(%s), instance(%s)",
        (int)config.service.size(), config.application.tenant.c_str(), config.application.name.c_str(),
        config.application.instance.c_str());
    ServiceMap services;
    for (unsigned int i = 0; i < config.service.size(); ++i) {
        const SentinelConfig::Service& serviceConfig = config.service[i];
        const vespalib::string name(serviceConfig.name);
        ServiceMap::iterator found(_services.find(name));
        if (found == _services.end()) {
            services[name] = Service::UP(new Service(serviceConfig, config.application, _outputConnections, _startMetrics));
        } else {
            found->second->reconfigure(serviceConfig);
            services[name] = std::move(found->second);
        }
    }
    _services.swap(services);
    vespalib::ComponentConfigProducer::Config current("sentinel", _subscriber.getGeneration(), "ok");
    _stateApi.myComponents.addConfig(current);
}


int
ConfigHandler::doWork()
{
    // Return true if there are any running services, false if not.

    if (_subscriber.nextGeneration(0)) {
        doConfigure();
    }

    handleCommands();
    handleOutputs();
    handleChildDeaths();
    _startMetrics.maybeLog();

    // Check for active services.
    for (ServiceMap::iterator it(_services.begin()), mt(_services.end()); it != mt; it++) {
        if (it->second->isRunning()) {
            return true;
        }
    }
    return false;
}


void
ConfigHandler::handleChildDeaths()
{
    // See if any of our child processes have exited, and take
    // the appropriate action.
    int status;
    pid_t pid;
    while ((pid = waitpid(-1, &status, WNOHANG)) > 0) {
        // A child process has exited. find it.
        Service *service = serviceByPid(pid);
        if (service != nullptr) {
            LOG(debug, "pid %d finished, Service:%s", (int)pid,
                service->name().c_str());
            service->youExited(status);
        } else {
            LOG(warning, "Unknown child pid %d exited (wait-status = %d)",
                (int)pid, status);
            EV_STOPPED("unknown", pid, status);
        }
    }
}

void
ConfigHandler::updateActiveFdset(fd_set *fds, int *maxNum)
{
    std::list<OutputConnection *>::const_iterator
        src = _outputConnections.begin();
    // ### _Possibly put an assert here if fd is > 1023???
    while (src != _outputConnections.end()) {
        OutputConnection *c = *src;
        ++src;
        int fd = c->fd();
        if (fd >= 0) {
            FD_SET(fd, fds);
            if (fd >= *maxNum) {
                *maxNum = fd + 1;
            }
        }
    }
    FD_SET(_commandSocket, fds);
    if (_commandSocket >= *maxNum) {
        *maxNum = _commandSocket + 1;
    }

    std::list<CommandConnection *>::const_iterator
        connections = _connections.begin();

    while (connections != _connections.end()) {
        CommandConnection *c = *connections;
        ++connections;
        int fd = c->fd();
        if (fd != -1) {
            FD_SET(fd, fds);
            if (fd >= *maxNum) {
                *maxNum = fd + 1;
            }
        }
    }
}

void
ConfigHandler::handleOutputs()
{
    std::list<OutputConnection *>::iterator dst;
    std::list<OutputConnection *>::const_iterator src;

    src = _outputConnections.begin();
    dst = _outputConnections.begin();
    while (src != _outputConnections.end()) {
        OutputConnection *c = *src;
        ++src;
        c->handleOutput();
        if (c->isFinished()) {
            LOG(debug, "Output is finished...");
            delete c;
        } else {
            *dst = c;
            ++dst;
        }
    }
    _outputConnections.erase(dst, _outputConnections.end());
}

void
ConfigHandler::handleCommands()
{
    {
        // handle RPC commands
        std::vector<Cmd::UP> got = _cmdQ.drain();
        for (const Cmd::UP & cmd : got) {
            handleCmd(*cmd);
        }
        // implicit return via Cmd destructor
    }

    // Accept new command connections, and read commands.
    int fd;
    struct sockaddr_storage sad;
    socklen_t sadLen = sizeof(sad);
    while ((fd = accept(_commandSocket,
                        reinterpret_cast<struct sockaddr *>(&sad),
                        &sadLen)) >= 0)
    {
        LOG(debug, "Got new command connection!");
        fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
        CommandConnection *c = new CommandConnection(fd);
        _connections.push_back(c);
    }

    std::list<CommandConnection *>::iterator dst;
    std::list<CommandConnection *>::const_iterator src;

    src = _connections.begin();
    dst = _connections.begin();
    while (src != _connections.end()) {
        CommandConnection *c = *src;
        ++src;
        handleCommand(c);
        if (c->isFinished()) {
            LOG(debug, "Connection is finished..");
            delete c;
        } else {
            *dst = c;
            ++dst;
        }
    }
    _connections.erase(dst, _connections.end());
}

Service *
ConfigHandler::serviceByPid(pid_t pid)
{
    for (ServiceMap::iterator it(_services.begin()), mt(_services.end()); it != mt; it++) {
        Service *service = it->second.get();
        if (service->pid() == pid) {
            return service;
        }
    }
    return nullptr;
}

Service *
ConfigHandler::serviceByName(const vespalib::string & name)
{
    ServiceMap::iterator found(_services.find(name));
    if (found != _services.end()) {
        return found->second.get();
    }
    return nullptr;
}


void
splitCommand(char *line, char *&cmd, char *&args)
{
    cmd = line;
    while (*line && !isspace(*line)) {
        *line = tolower(*line);
        ++line;
    }
    if (*line) {
        *line++ = '\0';
        while (*line && isspace(*line)) {
            ++line;
        }
    }
    args = line;
}

void
ConfigHandler::handleCmd(const Cmd& cmd)
{
    switch (cmd.type()) {
    case Cmd::LIST:
        {
            char retbuf[65536];
            size_t left = 65536;
            size_t pos = 0;
            for (ServiceMap::iterator it(_services.begin()), mt(_services.end()); it != mt; it++) {
                Service *service = it->second.get();
                const SentinelConfig::Service& config = service->serviceConfig();
                int sz = snprintf(retbuf + pos, left,
                                  "%s state=%s mode=%s pid=%d exitstatus=%d id=\"%s\"\n",
                                  service->name().c_str(), service->stateName(),
                                  service->isAutomatic() ? "AUTO" : "MANUAL",
                                  service->pid(), service->exitStatus(),
                                  config.id.c_str());
                pos += sz;
                left -= sz;
                if (left <= 0) break;
            }
            cmd.retValue(retbuf);
        }
        break;
    case Cmd::START:
        {
            Service *service = serviceByName(cmd.serviceName());
            if (service == nullptr) {
                cmd.retError("Cannot find named service");
                return;
            }
            service->setAutomatic(true);
            if (! service->isRunning()) {
                service->start();
            }
        }
        break;
    case Cmd::STOP:
        {
            Service *service = serviceByName(cmd.serviceName());
            if (service == nullptr) {
                cmd.retError("Cannot find named service");
                return;
            }
            service->setAutomatic(false);
            if (service->isRunning()) {
                service->terminate(true, false);
            }
        }
        break;
    }
}

void
ConfigHandler::handleCommand(CommandConnection *c)
{
    while (char *line = c->getCommand()) {
        LOG(debug, "Got command from connection: '%s'", line);

        char *cmd, *args;
        splitCommand(line, cmd, args);
        LOG(debug, "Command is '%s', args is '%s'", cmd, args);
        if (strcmp(cmd, "ls") == 0) {
            doLs(c, args);
        } else if (strcmp(cmd, "get") == 0) {
            doGet(c, args);
        } else if (strcmp(cmd, "restart") == 0) {
            doRestart(c, args);
        } else if (strcmp(cmd, "forcerestart") == 0) {
            doRestart(c, args, true);
        } else if (strcmp(cmd, "start") == 0) {
            doStart(c, args);
        } else if (strcmp(cmd, "stop") == 0) {
            doStop(c, args);
        } else if (strcmp(cmd, "forcestop") == 0) {
            doStop(c, args, true);
        } else if (strcmp(cmd, "auto") == 0) {
            doAuto(c, args);
        } else if (strcmp(cmd, "manual") == 0) {
            doManual(c, args);
        } else if (strcmp(cmd, "quit") == 0) {
            doQuit(c, args);
        } else {
            c->printf("ERROR: Unknown cmd '%s' "
                      "(ls/restart/start/stop/auto/manual/quit)\n", cmd);
        }
    }
}

void
ConfigHandler::updateMetrics()
{
    _startMetrics.maybeLog();
}

void
ConfigHandler::doGet(CommandConnection *c, char *args)
{
    char *path, *extra;
    splitCommand(args, path, extra);
    if (path[0] == '/') {
        updateMetrics();
        vespalib::string response = _stateApi.get(path);
        if (response.size() > 0) {
            c->printf("HTTP/1.0 200 OK\r\n"
                      "Content-Type: application/json; charset=ASCII\r\n\r\n");
            c->printf("%s", response.c_str());
            c->printf("\r\n");
        } else {
            c->printf("HTTP/1.0 404 Not found\r\n"
                      "Content-Type: text/plain; charset=ASCII\r\n\r\n"
                      "This web server only has metrics\r\n");
        }
    } else {
        c->printf("HTTP/1.0 400 Bad URL\r\nContent-Type: text/plain; charset=ASCII\r\n\r\nThis web server only has metrics\r\n");
    }
    c->finish();
    while (! c->isFinished()) {
        c->getCommand();
    }
}

void
ConfigHandler::doLs(CommandConnection *c, char *args)
{
    for (ServiceMap::iterator it(_services.begin()), mt(_services.end()); it != mt; it++) {
        Service *service = it->second.get();
        if (*args && strcmp(args, service->name().c_str()) != 0) {
            continue;
        }
        const SentinelConfig::Service& config = service->serviceConfig();
        c->printf("%s state=%s mode=%s pid=%d exitstatus=%d "
                  "autostart=%s autorestart=%s id=\"%s\"\n",
                  service->name().c_str(), service->stateName(),
                  service->isAutomatic() ? "AUTO" : "MANUAL",
                  service->pid(), service->exitStatus(),
                  config.autostart ? "TRUE" : "FALSE",
                  config.autorestart ? "TRUE" : "FALSE",
                  config.id.c_str());
    }
    c->printf("\n");
}

void
ConfigHandler::doQuit(CommandConnection *c, char *)
{
    c->printf("Exiting.\n");
    c->finish();
}

void
ConfigHandler::doStart(CommandConnection *c, char *args)
{
    Service *service = serviceByName(args);
    if (service == nullptr) {
        c->printf("Cannot find any service named '%s'\n", args);
        return;
    }

    if (service->isRunning()) {
        c->printf("ERROR: %s is already running as pid %d!\n", args,
                  service->pid());
    } else {
        service->resetRestartPenalty();
        service->start();
        c->printf("%s started as pid %d, mode=%s\n", args, service->pid(),
                  service->isAutomatic() ? "AUTO" : "MANUAL");
    }
}

void
ConfigHandler::doRestart(CommandConnection *c, char *args)
{
    doRestart(c, args, false);
}

void
ConfigHandler::doRestart(CommandConnection *c, char *args, bool force)
{
    Service *service = serviceByName(args);
    if (service == nullptr) {
        c->printf("Cannot find any service named '%s'\n", args);
        return;
    }

    if (!service->isRunning()) {
        service->resetRestartPenalty();
        service->start();
        c->printf("%s started as pid %d, mode=%s\n", args, service->pid(),
                  service->isAutomatic() ? "AUTO" : "MANUAL");
        return;
    }

    if (!service->isAutomatic()) {
        c->printf("ERROR: %s is in MANUAL mode, use stop+start\n", args);
        return;
    }
    const SentinelConfig::Service& config = service->serviceConfig();
    if (!config.autorestart) {
        c->printf("ERROR: %s does not autorestart, use stop+start\n", args);
        return;
    }
    c->printf("terminating service %s pid %d, will be autorestarted\n",
              args, service->pid());
    service->terminate(!force, false);
}

void
ConfigHandler::doStop(CommandConnection *c, char *args)
{
    doStop(c, args, false);
}

void
ConfigHandler::doStop(CommandConnection *c, char *args, bool force)
{
    Service *service = serviceByName(args);
    if (service == nullptr) {
        c->printf("Cannot find any service named '%s'\n", args);
        return;
    }

    if (!service->isRunning()) {
        c->printf("%s is not running, it is in state %s. Cannot stop.\n",
                  service->name().c_str(), service->stateName());
        return;
    }
    const SentinelConfig::Service& config = service->serviceConfig();
    if (service->isAutomatic() && config.autorestart) {
        c->printf("ERROR: %s in AUTO mode.  Use restart, or manual+stop.\n",
                  args);
        return;
    }
    c->printf("Stopping %s.\n", args);
    service->terminate(!force, false);
}

void
ConfigHandler::doAuto(CommandConnection *c, char *args)
{
    Service *service = serviceByName(args);
    if (service == nullptr) {
        c->printf("Cannot find any service named '%s'\n", args);
        return;
    }

    if (service->isAutomatic()) {
        c->printf("%s is already automatic.\n", args);
    } else {
        service->setAutomatic(true);
        const SentinelConfig::Service& config = service->serviceConfig();
        if (service->isRunning()) {
            c->printf("%s is now automatic again (and running).\n", args);
        } else if (config.autostart || config.autorestart) {
            service->start();
            c->printf("%s is now automatic again (and started).\n", args);
        } else {
            c->printf("%s is now automatic again (but not started)\n", args);
        }
    }
}


void
ConfigHandler::doManual(CommandConnection *c, char *args)
{
    Service *service = serviceByName(args);
    if (service == nullptr) {
        c->printf("Cannot find any service named '%s'\n", args);
        return;
    }

    if (!service->isAutomatic()) {
        c->printf("%s is already manual.\n", args);
    } else {
        service->setAutomatic(false);
        if (service->isRunning()) {
            c->printf("%s is now manual (but still running).\n", args);
        } else {
            c->printf("%s is now manual).\n", args);
        }
    }
}

}
