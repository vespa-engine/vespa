// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/metrics/dummy_metrics_manager.h>
#include <vespa/fastos/time.h>
#include <logd/forward.h>
#include <logd/metrics.h>
#include <sstream>
#include <fcntl.h>
#include <unistd.h>

using ns_log::Logger;
using namespace logdemon;

struct ForwardFixture {
    Forwarder & forwarder;
    int fd;
    const std::string fname;
    const std::string logLine;
    ForwardFixture(Forwarder & fw, const std::string & fileName)
        : forwarder(fw),
          fd(-1),
          fname(fileName),
          logLine(createLogLine())
    {
        fd = open(fileName.c_str(), O_CREAT | O_TRUNC | O_WRONLY, 0777);
        forwarder.setLogserverFD(fd);
    }
    ~ForwardFixture() {
        close(fd);
    }

    const std::string createLogLine() {
        FastOS_Time timer;
        timer.SetNow();
        std::stringstream ss;
        ss << std::fixed << timer.Secs();
        ss << "\texample.yahoo.com\t7518/34779\tlogd\tlogdemon\tevent\tstarted/1 name=\"logdemon\"\n";
        return ss.str();
    }

    void verifyForward(bool doForward) {
        const std::string & line(logLine);
        forwarder.forwardLine(line.c_str(), line.c_str() + line.length());
        fsync(fd);
        int rfd = open(fname.c_str(), O_RDONLY);
        char *buffer[2048];
        ssize_t bytes = read(rfd, buffer, 2048);
        ssize_t expected = doForward ? line.length() : 0;
        EXPECT_EQUAL(expected, bytes);
        close(rfd);
    }
};

std::shared_ptr<vespalib::metrics::MetricsManager> dummy = vespalib::metrics::DummyMetricsManager::create();
Metrics m(dummy);

TEST_FF("require that forwarder forwards if set", Forwarder(m), ForwardFixture(f1, "forward.txt")) {
    ForwardMap forwardMap;
    forwardMap[Logger::event] = true;
    f1.setForwardMap(forwardMap);
    f2.verifyForward(true);
}

TEST_FF("require that forwarder does not forward if not set", Forwarder(m), ForwardFixture(f1, "forward.txt")) {
    ForwardMap forwardMap;
    forwardMap[Logger::event] = false;
    f1.setForwardMap(forwardMap);
    f2.verifyForward(false);
}

TEST_MAIN() { TEST_RUN_ALL(); }
