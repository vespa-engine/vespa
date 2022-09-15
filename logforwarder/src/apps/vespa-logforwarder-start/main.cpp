// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <csignal>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-logforwarder-start");

#include "splunk-starter.h"
#include "splunk-stopper.h"
#include <vespa/vespalib/util/sig_catch.h>

class Wrapper {
    const char *_configId;
public:
    Wrapper(const char *cfid) : _configId(cfid) {}
    void run() {
        vespalib::SigCatch catcher;
        SplunkStarter handler;
        handler.start(_configId);
        while (! catcher.receivedStopSignal()) {
            handler.check();
            usleep(12500); // Avoid busy looping;
        }
        handler.stop();
    }
};

int
main(int argc, char** argv)
{
    int c = -1;
    bool stopMode = false;
    const char *cfid = nullptr;
    while ((c = getopt(argc, argv, "Sc:")) != -1) {
        switch (c) {
        case 'S':
            stopMode = true;
            break;
        case 'c':
            cfid = optarg;
            break;
        default:
            cfid = nullptr;
            break;
        }
    }
    if (cfid == nullptr) {
        LOG(error, "Usage: %s -c <config-id>", argv[0]);
        return EXIT_FAILURE;
    }
    if (stopMode) {
        SplunkStopper stopper(cfid);
        stopper.check();
    } else {
        Wrapper wrapper(cfid);
        wrapper.run();
    }
    return 0;
}
