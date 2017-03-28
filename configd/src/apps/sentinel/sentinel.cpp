// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <signal.h>
#include <cstring>
#include <unistd.h>
#include <sys/time.h>

#include <vespa/defaults.h>
#include <vespa/log/log.h>
LOG_SETUP("config-sentinel");
LOG_RCSID("$Id$");

#include <vespa/config-sentinel.h>

#include "config-handler.h"

using namespace config;

static int sigPermanent(int sig, void(*handler)(int));

static void gracefulShutdown(int sig);
static void sigchldHandler(int sig);

sig_atomic_t stop = 0;
static sig_atomic_t pendingWait = 0;

int
main(int argc, char **argv)
{
    int c = getopt(argc, argv, "c:");
    if (c != 'c') {
        LOG(error, "Usage: %s -c <config-id>", argv[0]);
        EV_STOPPING("config-sentinel", "Bad arguments on command line");
        exit(EXIT_FAILURE);
    }

    const char *configId = strdup(optarg);

    const char *rootDir = getenv("ROOT");
    if (!rootDir) {
        rootDir = vespa::Defaults::vespaHome();
        LOG(warning, "ROOT is not set, using %s", rootDir);
        setenv("ROOT", rootDir, 1);
    }

    if (chdir(rootDir) == -1) {
        LOG(error, "Fatal: Cannot cd to $ROOT (%s)", rootDir);
        EV_STOPPING("config-sentinel", "Cannot cd to $ROOT");
        exit(EXIT_FAILURE);
    }

    EV_STARTED("config-sentinel");

    sigPermanent(SIGPIPE, SIG_IGN);
    sigPermanent(SIGTERM, gracefulShutdown);
    sigPermanent(SIGINT, gracefulShutdown);
    sigPermanent(SIGCHLD, sigchldHandler);
    if (setenv("LC_ALL", "C", 1) != 0) {
        LOG(error, "Unable to set locale");
        exit(EXIT_FAILURE);
    }
    setlocale(LC_ALL, "C");

    sentinel::ConfigHandler handler;

    LOG(debug, "Reading configuration");
    try {
        handler.subscribe(configId);
    } catch (InvalidConfigException& ex) {
        LOG(error, "Fatal: Invalid configuration, please check your setup: %s", ex.getMessage().c_str());
        EV_STOPPING("config-sentinel", ex.what());
        exit(EXIT_FAILURE);
    } catch (ConfigRuntimeException& ex) {
        LOG(error, "Fatal: Could not get config, please check your setup: %s", ex.getMessage().c_str());
        EV_STOPPING("config-sentinel", ex.what());
        exit(EXIT_FAILURE);
    }

    struct timeval lastTv;
    gettimeofday(&lastTv, nullptr);
    while (!stop) {
        try {
            pendingWait = 0;
            handler.doWork();       // Check for child procs & commands
        } catch (InvalidConfigException& ex) {
            LOG(warning, "Configuration problem: (ignoring): %s",
                ex.what());
        }
        if (!pendingWait) {
            int maxNum = 0;
            fd_set fds;
            FD_ZERO(&fds);
            handler.updateActiveFdset(&fds, &maxNum);

            struct timeval tv;
            tv.tv_sec = 1;
            tv.tv_usec = 0;

            if (!pendingWait) {
                select(maxNum, &fds, nullptr, nullptr, &tv);
            }
        }

        struct timeval tv;
        gettimeofday(&tv, nullptr);
        double delta = tv.tv_sec - lastTv.tv_sec
                       + 1e-6 * tv.tv_usec - lastTv.tv_usec;
        if (delta < 0.01) {
            usleep(12500); // Avoid busy looping;
        }
        lastTv = tv;
    }

    int rv = handler.terminate();

    EV_STOPPING("config-sentinel", "normal exit");
    return rv;
}

static void
gracefulShutdown(int sig)
{
    (void)sig;
    stop = 1;
}

static void
sigchldHandler(int sig)
{
    (void)sig;
    pendingWait = 1;
}

static int
sigPermanent(int sig, void(*handler)(int))
{
    struct sigaction sa;

    memset(&sa, 0, sizeof(sa));
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0; // no SA_RESTART!
    sa.sa_handler = handler;
    return sigaction(sig, &sa, nullptr);
}

