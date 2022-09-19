// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "service.h"
#include "output-connection.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/signalhandler.h>

#include <csignal>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <sys/wait.h>

#include <vespa/log/log.h>
LOG_SETUP(".sentinel.service");
#include <vespa/log/llparser.h>

static bool stop()
{
    return (vespalib::SignalHandler::INT.check() ||
            vespalib::SignalHandler::TERM.check());
}

using vespalib::make_string;

namespace config::sentinel {

namespace {

vespalib::string getVespaTempDir() {
    vespalib::string tmp = getenv("ROOT");
    tmp += "/var/db/vespa/tmp";
    return tmp;
}

}

Service::Service(const SentinelConfig::Service& service, const SentinelConfig::Application& application,
                 std::list<OutputConnection *> &ocs, StartMetrics &metrics)
    : _pid(-1),
      _rawState(READY),
      _state(_rawState),
      _exitStatus(0),
      _config(new SentinelConfig::Service(service)),
      _isAutomatic(true),
      _restartPenalty(0),
      _last_start(vespalib::steady_time::min()),
      _application(application),
      _outputConnections(ocs),
      _metrics(metrics)
{
    LOG(debug, "%s: created", name().c_str());
    LOG(debug, "  command: %s", _config->command.c_str());
    LOG(debug, " configid: %s", _config->id.c_str());

    start();
}

void
Service::reconfigure(const SentinelConfig::Service& config)
{
    if (config.command != _config->command) {
        LOG(debug, "%s: reconfigured command '%s' -> '%s' - this will "
            "take effect at next restart", name().c_str(),
            _config->command.c_str(), config.command.c_str());
    }
    if (config.id != _config->id) {
        LOG(warning, "%s: reconfigured config id '%s' -> '%s' - signaling service restart",
            name().c_str(), _config->id.c_str(), config.id.c_str());
        terminate();
    }

    delete _config;
    _config = new SentinelConfig::Service(config);

    if ((_state == READY) || (_state == FINISHED) || (_state == RESTARTING)) {
        if (_isAutomatic) {
            LOG(debug, "%s: Restarting due to new config", name().c_str());
            start();
        }
    }
}

Service::~Service()
{
    terminate(false, false);
    delete _config;
}

void
Service::prepare_for_shutdown()
{
    auto cmd = _config->preShutdownCommand;
    if (cmd.empty()) {
        return;
    }
    if (_state == RUNNING) {
        // only run this once, before signaling the service:
        LOG(info, "prepare %s for shutdown: running %s", name().c_str(), cmd.c_str());
        runCommand(cmd);
    } else {
        LOG(info, "%s: not running, skipping preShutdownCommand(%s)", name().c_str(), cmd.c_str());
    }
}

int
Service::terminate(bool catchable, bool dumpState)
{
    if (isRunning()) {
        LOG(debug, "%s: terminate(%s)", name().c_str(), catchable ? "cleanly" : "NOW");
        resetRestartPenalty();
        kill(_pid, SIGCONT); // if it was stopped for some reason
        if (catchable) {
            if (_state != TERMINATING) {
                int ret = kill(_pid, SIGTERM);
                if (ret == 0) {
                    setState(TERMINATING);
                } else {
                    LOG(warning, "%s: kill -SIGTERM %d failed: %s",
                        name().c_str(), (int)_pid, strerror(errno));
                }
                return ret;
            }
            // already sent SIGTERM
            return 0;
        } else {
            if (dumpState && _state != KILLING) {
                vespalib::string pstackCmd = make_string("ulimit -c 0; pstack %d > %s/%s.pstack.%d 2>&1",
                                                         _pid, getVespaTempDir().c_str(), name().c_str(), _pid);
                LOG(info, "%s:%d failed to stop. Stack dumping with %s", name().c_str(), _pid, pstackCmd.c_str());
                int pstackRet = system(pstackCmd.c_str());
                if (pstackRet != 0) {
                    LOG(warning, "'%s' failed with return value %d", pstackCmd.c_str(), pstackRet);
                }
            }
            setState(KILLING);
            int ret = kill(_pid, SIGKILL);
            if (ret != 0) {
                LOG(warning, "%s: kill -SIGKILL %d failed: %s",
                    name().c_str(), (int)_pid, strerror(errno));
            }
            return ret;
        }
    }

    return 0; // Not running, so all is ok.
}

void
Service::runCommand(const std::string & command)
{
    int ret = system(command.c_str());
    if (ret == -1) {
        LOG(error, "%s: unable to run shutdown command (%s): %s", name().c_str(), command.c_str(), strerror(errno));
    } else if (WIFSIGNALED(ret)) {
        LOG(error, "%s: shutdown command (%s) terminated by signal %d", name().c_str(), command.c_str(), WTERMSIG(ret));
    } else if (ret != 0) {
        LOG(warning, "%s: shutdown command (%s) failed with exit status %d", name().c_str(), command.c_str(), WEXITSTATUS(ret));
    } else {
        LOG(info, "%s: shutdown command (%s) completed normally.", name().c_str(), command.c_str());
    }
}

void
Service::start()
{
    if (_state == REMOVING) {
        LOG(warning, "tried to start '%s' in REMOVING state", name().c_str());
        return;
    }
    _last_start = vespalib::steady_clock::now();

    setState(STARTING);

    int stdoutpipes[2];
    int err = pipe(stdoutpipes);
    int stderrpipes[2];
    err |= pipe(stderrpipes);

    if (err == -1) {
        LOG(error, "%s: Attempted to start, but pipe() failed: %s", name().c_str(),
            strerror(errno));
        setState(FAILED);
        return;
    }

    fflush(nullptr);
    _pid = fork();
    if (_pid == -1) {
        LOG(error, "%s: Attempted to start, but fork() failed: %s", name().c_str(),
            strerror(errno));
        setState(FAILED);
        close(stdoutpipes[0]);
        close(stdoutpipes[1]);
        close(stderrpipes[0]);
        close(stderrpipes[1]);
        return;
    }

    if (_pid == 0) {
        close(stdoutpipes[0]);
        close(stderrpipes[0]);

        close(1);
        dup2(stdoutpipes[1], 1);
        close(stdoutpipes[1]);

        close(2);
        dup2(stderrpipes[1], 2);
        close(stderrpipes[1]);

        LOG(debug, "%s: Started as pid %d", name().c_str(),
            static_cast<int>(getpid()));
        signal(SIGTERM, SIG_DFL);
        signal(SIGINT, SIG_DFL);
        if (stop()) {
            kill(getpid(), SIGTERM);
        }
        EV_STARTING(name().c_str());
        runChild(); // This function should not return.
        std::_Exit(EXIT_FAILURE);
    }

    close(stdoutpipes[1]);
    close(stderrpipes[1]);

    setState(RUNNING);
    _metrics.currentlyRunningServices++;
    _metrics.sentinel_running.sample(_metrics.currentlyRunningServices);

    using ns_log::LLParser;
    LLParser *p = new LLParser();
    p->setService(_config->name.c_str());
    p->setComponent("stdout");
    p->setPid(_pid);
    fcntl(stdoutpipes[0], F_SETFL,
          fcntl(stdoutpipes[0], F_GETFL) | O_NONBLOCK);
    OutputConnection *c = new OutputConnection(stdoutpipes[0], p);
    _outputConnections.push_back(c);

    p = new LLParser();
    p->setService(_config->name.c_str());
    p->setComponent("stderr");
    p->setPid(_pid);
    p->setDefaultLevel(ns_log::Logger::warning);
    fcntl(stderrpipes[0], F_SETFL,
          fcntl(stderrpipes[0], F_GETFL) | O_NONBLOCK);
    c = new OutputConnection(stderrpipes[0], p);
    _outputConnections.push_back(c);
}

void
Service::remove()
{
    LOG(info, "%s: removed from config", name().c_str());
    setAutomatic(false);
    terminate(false, false);
    setState(REMOVING);
}

void
Service::youExited(int status)
{
    // Someone did a waitpid() and figured out that we exited.
    _exitStatus = status;
    bool expectedDeath = (_state == KILLING || _state == TERMINATING
                          || _state == REMOVING
                          || _state == KILLED  || _state == TERMINATED);
    if (WIFEXITED(status)) {
        LOG(debug, "%s: Exited with exit code %d", name().c_str(),
            WEXITSTATUS(status));
        EV_STOPPED(name().c_str(), _pid, WEXITSTATUS(status));
        setState(FINISHED);
    } else if (WIFSIGNALED(status)) {
        if (expectedDeath) {
            EV_STOPPED(name().c_str(), _pid, WTERMSIG(status));
            LOG(debug, "%s: Exited expectedly by signal %d", name().c_str(),
                WTERMSIG(status));
            if (_state == TERMINATING) {
                setState(TERMINATED);
            } else if (_state == KILLING) {
                setState(KILLED);
            }
        } else {
            EV_CRASH(name().c_str(), _pid, WTERMSIG(status));
            setState(FAILED);
        }
    } else if (WIFSTOPPED(status)) {
        LOG(warning, "%s: STOPPED by signal %d!", name().c_str(), WSTOPSIG(status));
        setState(FAILED);
    } else {
        LOG(error, "%s: Weird exit code %d", name().c_str(), status);
        setState(FAILED);
    }
    _metrics.currentlyRunningServices--;
    _metrics.sentinel_running.sample(_metrics.currentlyRunningServices);

    if (! expectedDeath) {
        // make sure the service does not restart in a tight loop:
        vespalib::steady_time now = vespalib::steady_clock::now();
        vespalib::duration diff = now - _last_start;
        if (diff < MAX_RESTART_PENALTY) {
            incrementRestartPenalty();
        }
        if (diff > 10 * MAX_RESTART_PENALTY) {
            resetRestartPenalty();
        }
        if (diff < _restartPenalty) {
            LOG(info, "%s: will delay start by %2.3f seconds", name().c_str(), vespalib::to_s(_restartPenalty - diff));
        }
    }
    if (_isAutomatic && !stop()) {
        // ### Implement some rate limiting here maybe?
        LOG(debug, "%s: Restarting.", name().c_str());
        setState(RESTARTING);
        _metrics.totalRestartsCounter++;
        _metrics.sentinel_restarts.add();
    }
}

void
Service::runChild()
{
    // child process - this should exec or signal error
    for (int n = 3; n < 1024; ++n) { // Close all open fds on exec()
        fcntl(n, F_SETFD, FD_CLOEXEC);
    }

    for (const auto &envvar : _config->environ) {
        setenv(envvar.varname.c_str(), envvar.varvalue.c_str(), 1);
    }

    // Set up environment
    setenv("VESPA_SERVICE_NAME", _config->name.c_str(), 1);
    setenv("VESPA_CONFIG_ID", _config->id.c_str(), 1);
    setenv("VESPA_APPLICATION_TENANT", _application.tenant.c_str(), 1);
    setenv("VESPA_APPLICATION_NAME", _application.name.c_str(), 1);
    setenv("VESPA_APPLICATION_ENVIRONMENT", _application.environment.c_str(), 1);
    setenv("VESPA_APPLICATION_REGION", _application.region.c_str(), 1);
    setenv("VESPA_APPLICATION_INSTANCE", _application.instance.c_str(), 1);
    if (_config->affinity.cpuSocket >= 0) {
        setenv("VESPA_AFFINITY_CPU_SOCKET", std::to_string(_config->affinity.cpuSocket).c_str(), 1);
    }
    // ROOT is already set

    // Set up file descriptor 0 (1 and 2 should be setup already)
    close(0);
    int fd = open("/dev/null", O_RDONLY | O_NOCTTY, 0666);
    if (fd != 0) {
        char buf[200];
        snprintf(buf, sizeof buf, "open /dev/null for fd 0: got %d "
                                  "(%s)", fd, strerror(errno));
        [[maybe_unused]] auto writeRes = write(2, buf, strlen(buf));
        std::_Exit(EXIT_FAILURE);
    }
    fcntl(0, F_SETFD, 0); // Don't close on exec

    execl("/bin/sh", "/bin/sh", "-c", _config->command.c_str(), nullptr);

    char buf[200];
    snprintf(buf, sizeof buf, "exec error: %s for /bin/sh -c '%s'",
             strerror(errno), _config->command.c_str());
    [[maybe_unused]] auto writeRes = write(2, buf, strlen(buf));
    std::_Exit(EXIT_FAILURE);
}

const vespalib::string &
Service::name() const
{
    return _config->name;
}

bool
Service::isRunning() const
{
    switch (_state) {
    case READY:
    case FINISHED:
    case KILLED:
    case TERMINATED:
    case FAILED:
    case RESTARTING:
    case REMOVING:
        return false;

    case STARTING:
    case RUNNING:
    case TERMINATING:
    case KILLING:
        return true;
    }
    return true; // this will not be reached
}

bool
Service::wantsRestart() const
{
    if (_state == RESTARTING) {
        if (vespalib::steady_clock::now() > _last_start + _restartPenalty) {
            return true;
        }
    }
    return false;
}


void
Service::setAutomatic(bool autoStatus)
{
    _isAutomatic = autoStatus;
    resetRestartPenalty();
}


void
Service::incrementRestartPenalty()
{
    _restartPenalty += 1s;
    _restartPenalty *= 2;
    if (_restartPenalty > MAX_RESTART_PENALTY) {
        _restartPenalty = MAX_RESTART_PENALTY;
    }
    LOG(info, "%s: incremented restart penalty to %2.3f seconds", name().c_str(), vespalib::to_s(_restartPenalty));
}


void
Service::setState(ServiceState state)
{
    if (_state == REMOVING) {
        // ignore further changes
        return;
    }
    if (state != _state) {
        LOG(debug, "%s: %s->%s", name().c_str(), stateName(_state), stateName(state));
        _rawState = state;
    }

    // penalize failed services
    if (state == FAILED) {
        incrementRestartPenalty();
    }
}

const char *
Service::stateName(ServiceState state) const
{
    switch (state) {
    case READY: return "READY";
    case STARTING: return "STARTING";
    case RUNNING: return "RUNNING";
    case TERMINATING: return "TERMINATING";
    case KILLING: return "KILLING";
    case FINISHED: return "FINISHED";
    case TERMINATED: return "TERMINATED";
    case KILLED: return "KILLED";
    case FAILED: return "FAILED";
    case RESTARTING: return "RESTARTING";
    case REMOVING: return "REMOVING";
    }
    return "--BAD--";
}

}
