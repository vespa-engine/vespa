// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <csignal>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-logforwarder-start");

#include "cf-handler.h"
#include <vespa/vespalib/util/sig_catch.h>

class Wrapper {
    const char *_configId;
public:
    Wrapper(const char *cfid) : _configId(cfid) {}
    void run() {
        vespalib::SigCatch catcher;
        CfHandler handler;
        handler.start(_configId);
        while (! catcher.receivedStopSignal()) {
            handler.check();
            usleep(12500); // Avoid busy looping;
        }
    }
};

int
main(int argc, char** argv)
{
    int c = getopt(argc, argv, "c:");
    if (c != 'c') {
        LOG(error, "Usage: %s -c <config-id>", argv[0]);
        return EXIT_FAILURE;
    }
    Wrapper wrapper(optarg);
    wrapper.run();
    return 0;
}
