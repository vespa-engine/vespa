// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wrapper.h"
#include <csignal>
#include <unistd.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/sig_catch.h>

#include <vespa/defaults.h>
#include <vespa/log/log.h>
LOG_SETUP("vespa-otelcol-start");

static void run(const char *configId) {
    vespalib::SigCatch catcher;
    Wrapper handler(configId);
    try {
        handler.start(configId);
        while (! catcher.receivedStopSignal()) {
            handler.check();
            usleep(125000); // Avoid busy looping;
        }
    } catch (config::ConfigTimeoutException & ex) {
        LOG(warning, "Timout getting config, please check your setup. Will exit and restart: %s", ex.getMessage().c_str());
        std::_Exit(EXIT_FAILURE);
    } catch (config::InvalidConfigException& ex) {
        LOG(error, "Fatal: Invalid configuration, please check your setup: %s", ex.getMessage().c_str());
        std::_Exit(EXIT_FAILURE);
    } catch (config::ConfigRuntimeException& ex) {
        LOG(error, "Fatal: Could not get config, please check your setup: %s", ex.getMessage().c_str());
        std::_Exit(EXIT_FAILURE);
    }
    handler.stop();
};

int main(int argc, char** argv) {
    vespa::Defaults::bootstrap(argv[0]);
    int c = -1;
    const char *cfid = nullptr;
    while ((c = getopt(argc, argv, "c:")) != -1) {
        switch (c) {
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
    run(cfid);
    return 0;
}
