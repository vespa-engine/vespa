// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metrics.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/config-sentinel.h>
#include <list>

using cloud::config::SentinelConfig;

namespace config::sentinel {

class OutputConnection;

class Service
{
private:
    Service(const Service &);
    Service& operator=(const Service &);

    pid_t _pid;
    enum ServiceState { READY, STARTING, RUNNING, TERMINATING, KILLING,
                        RESTARTING, REMOVING,
                        FINISHED, TERMINATED, KILLED, FAILED } _rawState;
    const enum ServiceState& _state;
    int _exitStatus;
    SentinelConfig::Service *_config;
    bool _isAutomatic;

    static constexpr vespalib::duration MAX_RESTART_PENALTY = 1800s;
    vespalib::duration _restartPenalty;
    vespalib::steady_time _last_start;

    [[noreturn]] void runChild();
    void setState(ServiceState state);
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
    void prepare_for_shutdown();
    int terminate(bool catchable, bool dumpState);
    int terminate() {
        return terminate(true, false);
    }
    void start();
    void remove();
    void youExited(int status); // Call this if waitpid says it exited
    const vespalib::string & name() const;
    const char *stateName() const { return stateName(_state); }
    bool isRunning() const;
    bool wantsRestart() const;
    int exitStatus() const { return _exitStatus; }
    const SentinelConfig::Service& serviceConfig() const { return *_config; }
    void setAutomatic(bool autoStatus);
    bool isAutomatic() const { return _isAutomatic; }
    void resetRestartPenalty() { _restartPenalty = vespalib::duration::zero(); }
    void incrementRestartPenalty();
};

}
