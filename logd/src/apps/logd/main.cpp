// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>
#include <sys/stat.h>

#include <vespa/log/log.h>
LOG_SETUP("logdemon");
LOG_RCSID("$Id$");

#include <logd/errhandle.h>
#include <logd/sigterm.h>
#include <logd/service.h>
#include <logd/forward.h>
#include <logd/conf.h>
#include <logd/watch.h>
#include <logd/conn.h>
#include <vespa/config/common/exceptions.h>

using namespace logdemon;
using config::FileSpec;

int main(int, char**)
{
    Forwarder fwd;

    EV_STARTED("logdemon");

    hook_signals();

    const char *cfid = getenv("VESPA_CONFIG_ID");

    try {
        std::unique_ptr<ConfSub> subscriberP;
        subscriberP.reset(new ConfSub(fwd, config::ConfigUri(cfid)));
        ConfSub & subscriber(*subscriberP);

        int sleepcount = 0;
        while (true) {
            Watcher watcher(subscriber, fwd);

            try {
                subscriber.latch();
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
            if (gotSignaled()) {
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
                    if (gotSignaled()) {
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
        if (gotSignalNumber() == SIGTERM) {
            LOG(debug, "stopping on SIGTERM");
            EV_STOPPING("logdemon", "done ok.");
        } else {
            LOG(warning, "stopping on signal %d", gotSignalNumber());
            char buf[100];
            snprintf(buf, sizeof buf, "got signal %d", gotSignalNumber());
            EV_STOPPING("logdemon", buf);
        }
        return 0;
    } catch (MsgException& ex) {
        LOG(error, "stopping on error: %s", ex.what());
        EV_STOPPING("logdemon", "fatal error");
        return 1;
    } catch (...) {
        LOG(error, "unknown exception");
        EV_STOPPING("logdemon", "unknown error");
        return 1;
    }
    LOG(error, "connecting to logserver failed");
    EV_STOPPING("logdemon", "giving up after endless retries");
    return 1;
}
