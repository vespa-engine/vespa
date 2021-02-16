// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-handler.h"
#include "output-connection.h"

#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>
#include <string>
#include <fcntl.h>
#include <sys/wait.h>

#include <vespa/log/log.h>
LOG_SETUP(".config-handler");

namespace config::sentinel {

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
        throw vespalib::FatalException("Bad port " + std::to_string(port) + ", expected range [1, 65535]", VESPA_STRLOC);
    }
    if (port != _boundPort) {
        LOG(debug, "Config-sentinel accepts connections on port %d", port);
        _stateServer = std::make_unique<vespalib::StateServer>(
            port, _stateApi.myHealth, _startMetrics.producer, _stateApi.myComponents);
        _boundPort = port;
    }
}

ConfigHandler::ConfigHandler()
    : _subscriber(),
      _services(),
      _outputConnections(),
      _boundPort(0),
      _startMetrics(),
      _stateApi()
{
    _startMetrics.startedTime = vespalib::steady_clock::now();
}

ConfigHandler::~ConfigHandler()
{
    terminateServices(false);
    for (OutputConnection * conn : _outputConnections) {
        delete conn;
    }
}

void
ConfigHandler::terminateServices(bool catchable, bool printDebug)
{
    for (const auto & entry : _services) {
        Service *service = entry.second.get();
        service->setAutomatic(false);
        service->prepare_for_shutdown();
    }
    for (const auto & entry : _services) {
        Service *service = entry.second.get();
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
    vespalib::steady_time endTime = vespalib::steady_clock::now() + 58s;

    while ((vespalib::steady_clock::now() < endTime) && doWork()) {
        struct timeval tv {0, 200000};
        // Any child exiting will send SIGCHLD and break this select so
        // we handle the children exiting even quicker..
        select(0, nullptr, nullptr, nullptr, &tv);
    }
    for (int retry = 0; retry < 10 && doWork(); ++retry) {
        LOG(warning, "some services refuse to terminate cleanly, sending KILL");
        terminateServices(false, true);
        struct timeval tv {0, 200000};
        select(0, nullptr, nullptr, nullptr, &tv);
    }
    return !doWork();
}

void
ConfigHandler::subscribe(const std::string & configId, std::chrono::milliseconds timeout)
{
    _sentinelHandle = _subscriber.subscribe<SentinelConfig>(configId, timeout);
}

void
ConfigHandler::doConfigure()
{
    std::unique_ptr<SentinelConfig> cfg(_sentinelHandle->getConfig());
    const SentinelConfig& config(*cfg);

    if (config.port.telnet != _boundPort) {
        configure_port(config.port.telnet);
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
        auto found(_services.find(name));
        if (found == _services.end()) {
            services[name] = std::make_unique<Service>(serviceConfig, config.application, _outputConnections, _startMetrics);
        } else {
            found->second->reconfigure(serviceConfig);
            services[name] = std::move(found->second);
        }
    }
    _services.swap(services);
    for (auto & entry : services) {
        Service::UP svc = std::move(entry.second);
        if (svc && svc->isRunning()) {
            svc->remove();
            _orphans[entry.first] = std::move(svc);
        }
    }
    vespalib::ComponentConfigProducer::Config current("sentinel", _subscriber.getGeneration(), "ok");
    _stateApi.myComponents.addConfig(current);
}


int
ConfigHandler::doWork()
{
    // Return true if there are any running services, false if not.

    if (_subscriber.nextGenerationNow()) {
        doConfigure();
    }
    handleRestarts();
    handleCommands();
    handleOutputs();
    handleChildDeaths();
    _startMetrics.maybeLog();

    // Check for active services.
    for (const auto & service : _services) {
        if (service.second->isRunning()) {
            return true;
        }
    }
    return false;
}

void
ConfigHandler::handleRestarts()
{
    for (const auto & entry : _services) {
        Service & svc = *(entry.second);
        if (svc.wantsRestart()) {
            svc.start();
        }
    }
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
            _orphans.erase(service->name());
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
    // ### _Possibly put an assert here if fd is > 1023???
    for (OutputConnection *c : _outputConnections) {
        int fd = c->fd();
        if (fd >= 0) {
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
    // handle RPC commands
    std::vector<Cmd::UP> got = _cmdQ.drain();
    for (const Cmd::UP & cmd : got) {
        handleCmd(*cmd);
    }
    // implicit return via Cmd destructor
}

Service *
ConfigHandler::serviceByPid(pid_t pid)
{
    for (const auto & service : _services) {
        if (service.second->pid() == pid) {
            return service.second.get();
        }
    }
    for (const auto & it : _orphans) {
        Service *service = it.second.get();
        if (service->pid() == pid) {
            return service;
        }
    }
    return nullptr;
}

Service *
ConfigHandler::serviceByName(const vespalib::string & name)
{
    auto found(_services.find(name));
    if (found != _services.end()) {
        return found->second.get();
    }
    return nullptr;
}


void
ConfigHandler::handleCmd(const Cmd& cmd)
{
    switch (cmd.type()) {
    case Cmd::LIST:
        {
            char retbuf[64_Ki];
            size_t left = 64_Ki;
            size_t pos = 0;
            retbuf[pos] = 0;
            for (const auto & entry : _services) {
                const Service *service = entry.second.get();
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
            retbuf[65535] = 0;
            cmd.retValue(retbuf);
        }
        break;
    case Cmd::RESTART:
        {
            Service *service = serviceByName(cmd.serviceName());
            if (service == nullptr) {
                cmd.retError("Cannot find named service");
                return;
            }
            service->setAutomatic(true);
            service->resetRestartPenalty();
            if (service->isRunning()) {
                service->terminate(true, false);
            } else {
                service->start();
            }
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
            service->resetRestartPenalty();
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
ConfigHandler::updateMetrics()
{
    _startMetrics.maybeLog();
}

}
