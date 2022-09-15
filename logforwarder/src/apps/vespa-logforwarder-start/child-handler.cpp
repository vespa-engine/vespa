// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "child-handler.h"

#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <vector>
#include <string>
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP(".child-handler");

ChildHandler::ChildHandler() : _childRunning(false) {}

namespace {

void
runSplunk(const vespalib::string &prefix, std::vector<const char *> args)
{
    vespalib::string path = prefix + "/bin/splunk";
    args.insert(args.begin(), path.c_str());
    std::string dbg = "";
    for (const char *arg : args) {
        dbg.append(" '");
        dbg.append(arg);
        dbg.append("'");
    }
    LOG(info, "trigger splunk with command: %s", dbg.c_str());
    args.push_back(nullptr);
    fflush(stdout);
    pid_t child = fork();
    if (child == -1) {
        perror("fork()");
        return;
    }
    if (child == 0) {
        vespalib::string env = "SPLUNK_HOME=" + prefix;
        char *cenv = const_cast<char *>(env.c_str()); // safe cast
        putenv(cenv);
        LOG(debug, "added to environment: '%s'", cenv);
        char **cargv = const_cast<char **>(args.data()); // safe cast
        execv(cargv[0], cargv);
        // if execv fails:
        perror(cargv[0]);
        std::_Exit(1);
    }
    LOG(debug, "child running with pid %d", (int)child);
    int waitStatus = 0;
    if (waitpid(child, &waitStatus, 0) == -1) {
        perror("waitpid()");
        return;
    }
    if (WIFEXITED(waitStatus) && WEXITSTATUS(waitStatus) == 0) {
        // all OK
        LOG(debug, "child ran ok, exit status 0");
        return;
    }
    if (WIFEXITED(waitStatus)) {
        LOG(warning, "failed triggering splunk (exit status %d)",
            WEXITSTATUS(waitStatus));
    } else if (WIFSIGNALED(waitStatus)) {
        LOG(warning, "failed triggering splunk (exit on signal %d)",
            WTERMSIG(waitStatus));
    } else {
        LOG(warning, "failed triggering splunk (abnormal exit status %d)",
            waitStatus);
    }
}

} // namespace <unnamed>


void
ChildHandler::startChild(const vespalib::string &prefix)
{
    LOG(debug, "startChild '%s'", prefix.c_str());
    if (_childRunning && prefix == _runningPrefix) {
        runSplunk(prefix, {"restart"});
    } else {
        if (_childRunning) {
            runSplunk(_runningPrefix, {"stop"});
        } else {
            // it is possible that splunk was already running anyway, so
            // make sure we restart it to get new config activated:
            runSplunk(prefix, {"stop"});
        }
        sleep(1);
        runSplunk(prefix, {"start", "--answer-yes", "--no-prompt", "--accept-license"});
        _childRunning = true;
        _runningPrefix = prefix;
    }
}

void ChildHandler::stopChild() {
    if (_runningPrefix != "") {
        LOG(debug, "stopChild '%s'", _runningPrefix.c_str());
        runSplunk(_runningPrefix, {"stop"});
    }
    _childRunning = false;
}

void
ChildHandler::stopChild(const vespalib::string &prefix) {
    stopChild();
    _runningPrefix = prefix;
    stopChild();
}
