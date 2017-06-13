// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/config-sentinel.h>
#include <list>

#include "metrics.h"

using cloud::config::SentinelConfig;

namespace config {
namespace sentinel {

class OutputConnection;

class Service
{
private:
    Service(const Service &);
    Service& operator=(const Service &);

    pid_t _pid;
    enum ServiceState { READY, STARTING, RUNNING, TERMINATING, KILLING,
                        FINISHED, TERMINATED, KILLED, FAILED } _rawState;
    const enum ServiceState& _state;
    int _exitStatus;
    SentinelConfig::Service *_config;
    bool _isAutomatic;

    static const unsigned int MAX_RESTART_PENALTY = 60;
    unsigned int _restartPenalty;
    time_t _last_start;

    void runChild(int pipes[2]) __attribute__((noreturn));
    void ensureChildRuns(int fd);
    void setState(ServiceState state);
    void runPreShutdownCommand();
    void runCommand(const std::string & command);
    const char *stateName(ServiceState state) const;

    const SentinelConfig::Application _application;
    std::list<OutputConnection *> &_outputConnections;

    StartMetrics &_metrics;

public:
    using UP = std::unique_ptr<Service>;
    ~Service();
    Service(const SentinelConfig::Service& config,
            const SentinelConfig::Application& application,
	    std::list<OutputConnection *> &ocs,
            StartMetrics &metrics);
    void reconfigure(const SentinelConfig::Service& config);
    int pid() const { return _pid; }
    int terminate(bool catchable);
    int start();
    void youExited(int status); // Call this if waitpid says it exited
    const vespalib::string & name() const;
    const char *stateName() const { return stateName(_state); }
    bool isRunning() const;
    int exitStatus() const { return _exitStatus; }
    const SentinelConfig::Service& serviceConfig() const { return *_config; }
    void setAutomatic(bool autoStatus);
    bool isAutomatic() const { return _isAutomatic; }
    void resetRestartPenalty() { _restartPenalty = 0; }
    void incrementRestartPenalty();
};

} // end namespace sentinel
} // end namespace config

