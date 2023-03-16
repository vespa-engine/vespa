// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "logctl.h"

#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>

#include <vespa/log/log.h>
LOG_SETUP(".sentinel.logctl");

namespace config::sentinel {

void justRunLogctl(const char *cspec, const char *lspec)
{
    const char *progName = "vespa-logctl";
    pid_t pid = fork();
    if (pid == 0) {
        LOG(debug, "running '%s' '%s' '%s'", progName, cspec, lspec);
        int devnull = open("/dev/null", O_WRONLY);
        if (devnull > 1) {
            dup2(devnull, 1);
            close(devnull);
        }
        int rv = execlp(progName, progName, "-c", cspec, lspec, nullptr);
        if (rv != 0) {
            LOG(warning, "execlp of '%s' failed: %s", progName, strerror(errno));
        }
    } else if (pid > 0) {
        bool again;
        do {
            again = false;
            int wstatus = 0;
            pid_t got = waitpid(pid, &wstatus, 0);
            if (got == pid) {
                again = false;
                if (WIFEXITED(wstatus)) {
                    int exitCode = WEXITSTATUS(wstatus);
                    if (exitCode != 0) {
                        LOG(warning, "running '%s' failed (exit code %d)", progName, exitCode);
                    }
                } else if (WIFSIGNALED(wstatus)) {
                    int termSig = WTERMSIG(wstatus);
                    LOG(warning, "running '%s' failed (got signal %d)", progName, termSig);
                } else {
                    LOG(warning, "'%s' failure (wait status was %d)", progName, wstatus);
                }
            } else if (errno == EINTR) {
                again = true;
            } else {
                LOG(error, "waitpid() failed: %s", strerror(errno));
            }
        } while (again);
    } else {
        LOG(error, "fork() failed: %s", strerror(errno));
    }
}

}
