// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <stdlib.h>
#include <unistd.h>
#include <csignal>

#include <vespa/log/log.h>

LOG_SETUP("logtest",
          "$Id: logtest.cpp 119439 2011-04-19 09:32:27Z arnej $");

int
main(int, char **argv)
{
    EV_STARTING("logtest");
    LOG(info, "Starting up, called as %s", argv[0]);
    EV_STARTED("logtest");
    EV_CRASH("something", getpid(), SIGTERM);
    EV_PROGRESS("batch-index", 7, 100);
    EV_PROGRESS("unbounded-batch-index", 9);
    EV_COUNT("hits", 3);
    EV_VALUE("some value", 1./3);

    LOG(info, "backslash: \\");
    int n;
    LOG(info, "Will log 20 spam messages now every 50ms");
    for (n = 1; n <= 5; n++) {
        LOG(spam, "log message %d/%d", n, 100);
        usleep(50000);
    }
    LOG(info, "Exiting.");
    EV_STOPPING("logtest", "clean exit");
    EV_STOPPED("logtest", getpid(), 0);
    return EXIT_SUCCESS;
}
