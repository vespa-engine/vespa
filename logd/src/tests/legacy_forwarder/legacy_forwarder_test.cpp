// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <logd/legacy_forwarder.h>
#include <logd/metrics.h>
#include <vespa/fastos/time.h>
#include <vespa/log/log.h>
#include <vespa/vespalib/metrics/dummy_metrics_manager.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <fcntl.h>
#include <sstream>
#include <unistd.h>

using ns_log::Logger;
using namespace logdemon;

std::shared_ptr<vespalib::metrics::MetricsManager> dummy = vespalib::metrics::DummyMetricsManager::create();
Metrics m(dummy);

struct ForwardFixture {
    LegacyForwarder::UP forwarder;
    int fd;
    const std::string fname;
    const std::string logLine;
    ForwardFixture(const std::string& fileName)
        : forwarder(),
          fd(-1),
          fname(fileName),
          logLine(createLogLine())
    {
        fd = open(fileName.c_str(), O_CREAT | O_TRUNC | O_WRONLY, 0777);
    }
    ~ForwardFixture() {
    }

    void make_forwarder(const ForwardMap& forwarder_filter) {
        forwarder = LegacyForwarder::to_open_file(m, forwarder_filter, fd);
    }

    const std::string createLogLine() {
        FastOS_Time timer;
        timer.SetNow();
        std::stringstream ss;
        ss << std::fixed << timer.Secs();
        ss << "\texample.yahoo.com\t7518/34779\tlogd\tlogdemon\tevent\tstarted/1 name=\"logdemon\"";
        return ss.str();
    }

    void verifyForward(bool doForward) {
        forwarder->forwardLine(logLine);
        fsync(fd);
        int rfd = open(fname.c_str(), O_RDONLY);
        char *buffer[2048];
        ssize_t bytes = read(rfd, buffer, 2048);
        ssize_t expected = doForward ? logLine.length() + 1 : 0;
        EXPECT_EQUAL(expected, bytes);
        close(rfd);
    }
};


TEST_F("require that forwarder forwards if set", ForwardFixture("forward.txt")) {
    ForwardMap forward_filter;
    forward_filter[Logger::event] = true;
    f1.make_forwarder(forward_filter);
    f1.verifyForward(true);
}

TEST_F("require that forwarder does not forward if not set", ForwardFixture("forward.txt")) {
    ForwardMap forward_filter;
    forward_filter[Logger::event] = false;
    f1.make_forwarder(forward_filter);
    f1.verifyForward(false);
}

TEST_MAIN() { TEST_RUN_ALL(); }
