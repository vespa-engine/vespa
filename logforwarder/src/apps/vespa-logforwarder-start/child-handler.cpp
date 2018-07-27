// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "child-handler.h"

#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <vespa/log/log.h>
LOG_SETUP(".child-handler");

ChildHandler::ChildHandler() : _childRunning(false) {}

namespace {

void
runSplunk(const vespalib::string &prefix, const char *a1, const char *a2 = 0)
{
    const char *argv[] = { 0, a1, a2, 0 };
    vespalib::string path = prefix + "/bin/splunk";
    argv[0] = path.c_str();
    LOG(debug, "starting splunk forwarder with command: '%s' '%s' '%s'",
        argv[0], argv[1], argv[2]);
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
        char **cargv = const_cast<char **>(argv); // safe cast
        execv(argv[0], cargv);
        // if execv fails:
        perror(argv[0]);
        exit(1);
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
        LOG(warning, "failed starting splunk forwarder (exit status %d)",
            WEXITSTATUS(waitStatus));
    } else if (WIFSIGNALED(waitStatus)) {
        LOG(warning, "failed starting splunk forwarder (exit on signal %d)",
            WTERMSIG(waitStatus));
    } else {
        LOG(warning, "failed starting splunk forwarder (abnormal exit status %d)",
            waitStatus);
    }
}

} // namespace <unnamed>


void
ChildHandler::startChild(const vespalib::string &prefix)
{
    if (! _childRunning) {
        runSplunk(prefix, "start", "--answer-yes", "--no-prompt", "--accept-license");
        _childRunning = true;
        // it is possible that splunk was already running, and
        // then the above won't do anything, so we need to
        // *also* do the restart below, after a small delay.
        sleep(1);
    }
    runSplunk(prefix, "restart");
}

void
ChildHandler::stopChild(const vespalib::string &prefix)
{
    runSplunk(prefix, "stop");
    _childRunning = false;
}
