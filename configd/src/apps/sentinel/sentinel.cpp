// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-handler.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/defaults.h>
#include <chrono>
#include <string>
#include <unistd.h>
#include <sys/time.h>

#include <vespa/log/log.h>
LOG_SETUP("config-sentinel");

using namespace config;

constexpr std::chrono::milliseconds CONFIG_TIMEOUT_MS(3 * 60 * 1000);

static bool stop()
{
    return (vespalib::SignalHandler::INT.check() ||
            vespalib::SignalHandler::TERM.check());
}

int
main(int argc, char **argv)
{
    int c = getopt(argc, argv, "c:");
    if (c != 'c') {
        LOG(error, "Usage: %s -c <config-id>", argv[0]);
        EV_STOPPING("config-sentinel", "Bad arguments on command line");
        return EXIT_FAILURE;
    }

    std::string configId(optarg);

    const char *rootDir = getenv("ROOT");
    if (!rootDir) {
        rootDir = vespa::Defaults::vespaHome();
        LOG(warning, "ROOT is not set, using %s", rootDir);
        setenv("ROOT", rootDir, 1);
    }

    if (chdir(rootDir) == -1) {
        LOG(error, "Fatal: Cannot cd to $ROOT (%s)", rootDir);
        EV_STOPPING("config-sentinel", "Cannot cd to $ROOT");
        return EXIT_FAILURE;
    }

    EV_STARTED("config-sentinel");

    vespalib::SignalHandler::PIPE.ignore();
    vespalib::SignalHandler::TERM.hook();
    vespalib::SignalHandler::INT.hook();
    vespalib::SignalHandler::CHLD.hook();

    if (setenv("LC_ALL", "C", 1) != 0) {
        LOG(error, "Unable to set locale");
        return EXIT_FAILURE;
    }
    setlocale(LC_ALL, "C");

    sentinel::ConfigHandler handler;

    LOG(debug, "Reading configuration");
    try {
        handler.subscribe(configId, CONFIG_TIMEOUT_MS);
    } catch (ConfigTimeoutException & ex) {
        LOG(warning, "Timeout getting config, please check your setup. Will exit and restart: %s", ex.getMessage().c_str());
        EV_STOPPING("config-sentinel", ex.what());
        return EXIT_FAILURE;
    } catch (InvalidConfigException& ex) {
        LOG(error, "Fatal: Invalid configuration, please check your setup: %s", ex.getMessage().c_str());
        EV_STOPPING("config-sentinel", ex.what());
        return EXIT_FAILURE;
    } catch (ConfigRuntimeException& ex) {
        LOG(error, "Fatal: Could not get config, please check your setup: %s", ex.getMessage().c_str());
        EV_STOPPING("config-sentinel", ex.what());
        return EXIT_FAILURE;
    }

    vespalib::steady_time lastTime = vespalib::steady_clock::now();
    while (!stop()) {
        try {
            vespalib::SignalHandler::CHLD.clear();
            handler.doWork();       // Check for child procs & commands
        } catch (InvalidConfigException& ex) {
            LOG(warning, "Configuration problem: (ignoring): %s", ex.what());
        } catch (vespalib::PortListenException& ex) {
            LOG(error, "Fatal: %s", ex.getMessage().c_str());
            EV_STOPPING("config-sentinel", ex.what());
            return EXIT_FAILURE;
        } catch (vespalib::FatalException& ex) {
            LOG(error, "Fatal: %s", ex.getMessage().c_str());
            EV_STOPPING("config-sentinel", ex.what());
            return EXIT_FAILURE;
        }
        if (vespalib::SignalHandler::CHLD.check()) {
            continue;
        }
        int maxNum = 0;
        fd_set fds;
        FD_ZERO(&fds);
        handler.updateActiveFdset(&fds, &maxNum);

        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 100000;  //0.1s

        select(maxNum, &fds, nullptr, nullptr, &tv);

        vespalib::steady_time now = vespalib::steady_clock::now();
        if ((now - lastTime) < 10ms) {
            std::this_thread::sleep_for(12ms); // Avoid busy looping;
        }
        lastTime = now;
    }

    EV_STOPPING("config-sentinel", "normal exit");
    int rv = handler.terminate();
    return rv;
}
