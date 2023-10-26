// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/signalhandler.h>
#include <cstdio>
#include <unistd.h>

using vespalib::SignalHandler;

int main(int argc, char **argv) {
    (void) argc;
    (void) argv;
    SignalHandler::TERM.hook();
    kill(getpid(), SIGTERM);
    if (SignalHandler::TERM.check()) {
        fprintf(stdout, "GOT TERM\n");
        fflush(stdout);
    }
    SignalHandler::TERM.unhook();
    kill(getpid(), SIGTERM);
    fprintf(stdout, "SURVIVED TERM\n");
    fflush(stdout);
    return 0;
}
