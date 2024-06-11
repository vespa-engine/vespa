// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "child-handler.h"

#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <sys/wait.h>
#include <vector>
#include <string>
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP(".child-handler");

ChildHandler::ChildHandler() : _childRunning(false), _childPid(0) {}

ChildHandler::~ChildHandler() = default;

bool ChildHandler::checkChild() {
    if (! _childRunning) return true;
    int waitStatus = 0;
    int r = waitpid(_childPid, &waitStatus, WNOHANG);
    if (r == 0) {
        return false;
    }
    if (r < 0) {
        perror("waitpid");
        // XXX how to handle?
        return false;
    }
    _childRunning = false;
    if (WIFEXITED(waitStatus) && WEXITSTATUS(waitStatus) == 0) {
        // all OK
        LOG(info, "child ran ok, exit status 0");
    } else if (WIFEXITED(waitStatus)) {
        LOG(warning, "child failed (exit status %d)", WEXITSTATUS(waitStatus));
    } else if (WIFSIGNALED(waitStatus)) {
        if (_terminating) {
            LOG(info, "child terminated (using signal %d)", WTERMSIG(waitStatus));
        } else {
            LOG(warning, "child failed (exit on signal %d)", WTERMSIG(waitStatus));
        }
    } else {
        LOG(warning, "child failed (abnormal exit status %d)", waitStatus);
    }
    return true;
}

void ChildHandler::startChild(const std::string &progPath, const std::string &cfPath) {
    _terminating = false;
    LOG(info, "startChild '%s' '%s'", progPath.c_str(), cfPath.c_str());
    pid_t child = fork();
    if (child == -1) {
        perror("fork()");
        return;
    }
    if (child == 0) {
        std::string cfArg1{"--config=file:" "/etc/otelcol/gw-config.yaml"};
        std::string cfArg2{"--config=file:" + cfPath};
        const char *cargv[] = { progPath.c_str(), cfArg1.c_str(), cfArg2.c_str(), nullptr };
        execv(progPath.c_str(), const_cast<char **>(cargv));
        // if execv fails:
        perror(progPath.c_str());
        std::_Exit(1);
    }
    LOG(info, "child running with pid %d", (int)child);
    _childRunning = true;
    _childPid = child;
}

void ChildHandler::stopChild() {
    if (! _childRunning) return;
    LOG(info, "stopChild");
    _terminating = true;
    kill(_childPid, SIGTERM);
    for (int retry = 0; retry < 10; ++retry) {
        if (checkChild()) return;
        usleep(12500 + retry * 20000);
    }
    kill(_childPid, SIGKILL);
    for (int retry = 0; retry < 10; ++retry) {
        if (checkChild()) return;
        usleep(12500 + retry * 20000);
    }
    LOG(error, "Could not terminete child process %d", _childPid);
}
