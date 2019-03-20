// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <logd/config_subscriber.h>
#include <logd/errhandle.h>
#include <logd/forwarder.h>
#include <logd/metrics.h>
#include <logd/state.h>
#include <logd/watch.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/sig_catch.h>
#include <csignal>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("logdemon");

using namespace logdemon;
using config::FileSpec;

int main(int, char**)
{
    StateReporter stateReporter;
    Metrics metrics(stateReporter.metrics());
    Forwarder fwd(metrics);

    EV_STARTED("logdemon");

    vespalib::SigCatch catcher;

    const char *cfid = getenv("VESPA_CONFIG_ID");

    try {
        ConfigSubscriber subscriber(fwd, config::ConfigUri(cfid));

        int sleepcount = 0;
        while (true) {
            Watcher watcher(subscriber, fwd);

            try {
                subscriber.latch();
                stateReporter.setStatePort(subscriber.getStatePort());
                stateReporter.gotConf(subscriber.generation());
                int fd = subscriber.getservfd();
                if (fd >= 0) {
                    sleepcount = 0 ; // connection OK, reset sleep time
                    watcher.watchfile();
                } else {
                    LOG(spam, "bad fd in subscriber");
                }
            } catch (ConnectionException& ex) {
                LOG(debug, "connection exception: %s", ex.what());
                subscriber.closeConn();
            }
            if (catcher.receivedStopSignal()) {
                throw SigTermException("caught signal");
            }
            if (sleepcount < 60) {
                ++sleepcount;
            } else {
                sleepcount = 60;
            }
            LOG(debug, "sleep %d...", sleepcount);
            for (int i = 0; i < sleepcount; i++) {
                sleep(1);
                if (catcher.receivedStopSignal()) {
                    throw SigTermException("caught signal");
                }
            }
        }
    } catch (config::ConfigRuntimeException & ex) {
        LOG(error, "Configuration failed: %s", ex.what());
        EV_STOPPING("logdemon", "bad config");
        return 1;
    } catch (config::InvalidConfigException & ex) {
        LOG(error, "Configuration failed: %s", ex.what());
        EV_STOPPING("logdemon", "bad config");
        return 1;
    } catch (SigTermException& ex) {
        LOG(debug, "stopping on SIGTERM");
        EV_STOPPING("logdemon", "done ok.");
        return 0;
    } catch (MsgException& ex) {
        LOG(error, "stopping on error: %s", ex.what());
        EV_STOPPING("logdemon", "fatal error");
        return 1;
    } catch (std::exception & ex) {
        LOG(error, "unknown exception: %s", ex.what());
        EV_STOPPING("logdemon", "unknown error");
        return 1;
    }
    LOG(error, "connecting to logserver failed");
    EV_STOPPING("logdemon", "giving up after endless retries");
    return 1;
}
