// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadstackexecutor.h>
#include <chrono>
#include <mutex>
#include <condition_variable>

namespace search::bmcluster {

class BmCluster;

class BmNodeStatsReporter {
    BmCluster&                    _cluster;
    vespalib::ThreadStackExecutor _executor;
    std::mutex                    _mutex;
    std::condition_variable       _cond;
    uint32_t                      _pending_report;
    bool                          _started;
    bool                          _stop;

    void report();
    void run_report_loop(std::chrono::milliseconds interval);
public:
    BmNodeStatsReporter(BmCluster& cluster);
    ~BmNodeStatsReporter();
    void start(std::chrono::milliseconds interval);
    void stop();
    void report_now();
};

}
