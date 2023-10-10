// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <csignal>
#include <cstdlib>

int main(int argc, char *argv[])
{
    int   lfpipe[2];
    pid_t lfmtpid;
    pid_t progpid;
    int   wstat;

    if (argc < 3) {
        fprintf(stderr, "Usage: %s logfmt prog [...]\n", argv[0]);
        return 1;
    }

    pipe(lfpipe);
    lfmtpid = fork();
    if (lfmtpid == 0) {
        close(lfpipe[1]);
        if (lfpipe[0] != 0) {
            close(0);
            dup(lfpipe[0]);
            close(lfpipe[0]);
        }
        execlp(argv[1], argv[1], "-", (const char *)NULL);
        perror("exec logfmt failed");
        std::_Exit(1);
    }
    if (lfmtpid < 0) {
        perror("fork failed");
        return 1;
    }
    close(lfpipe[0]);

    progpid = fork();
    if (progpid == 0) {
        char buf[20];
        int i=2;

        sprintf(buf, "fd:%d", lfpipe[1]);
        setenv("VESPA_LOG_TARGET", buf, 1);
        for (; i<argc; i++) {
            argv[i-2] = argv[i];
        }
        argv[i-2] = NULL;
        execvp(argv[0], argv);
        perror("exec program failed");
        std::_Exit(1);
    }
    if (progpid < 0) {
        perror("fork failed");
        return 1;
    }
    close(lfpipe[1]);

    if (waitpid(lfmtpid, &wstat, 0) != lfmtpid) {
        perror("bad waitpid for logfmt");
    }
    if (waitpid(progpid, &wstat, 0) != progpid) {
        perror("bad waitpid for program");
    }

    if (WIFEXITED(wstat)) {
        return WEXITSTATUS(wstat);
    }
    if (WIFSIGNALED(wstat)) {
        int sig = WTERMSIG(wstat);
        signal(sig, SIG_DFL);
        kill(getpid(), sig);
    }
    /* should not get here */
    return 1;
}
