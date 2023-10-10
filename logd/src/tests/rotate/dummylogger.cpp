// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <vespa/log/log.h>
LOG_SETUP("dummylogger");

int
main(int argc, char **argv)
{
    (void) argc;
    (void) argv;
    int cnt = 100000;
    for (int i = 0; i < cnt; ++i) {
        LOG(info, "This is log message %d/%d", i + 1, cnt);
        if ((i % 100) == 0) {
            struct timespec t;
            t.tv_sec  = 0;
            t.tv_nsec = 250000000;
            nanosleep(&t, 0);
        }
        if ((i % 1000) == 999) {
            int percent = (i + 1) / 1000;
            fprintf(stdout, "log progress: %d%%\n", percent);
            fflush(stdout);
        }
    }
    return 0;
}
