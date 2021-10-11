// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadstackexecutor.h>
#include <chrono>
#include <mutex>
#include <condition_variable>

namespace search::bmcluster {

class BmCluster;
class BmNodeStats;

/*
 * Class handling background reporting of node stats during feed or
 * document redistribution.
 */
class BmNodeStatsReporter {
public:
    struct MergeCounts {
        uint64_t                  _source_unchanged_count;
        uint64_t                  _retry_merge_count;
        uint64_t                  _delete_after_merge_count;

        MergeCounts();
        MergeCounts& operator-=(const MergeCounts& rhs);
        void sample();
    };
private:
    BmCluster&                    _cluster;
    vespalib::ThreadStackExecutor _executor;
    std::mutex                    _mutex;
    std::condition_variable       _cond;
    std::chrono::time_point<std::chrono::steady_clock> _change_time;
    std::vector<BmNodeStats>      _prev_node_stats;
    uint32_t                      _pending_report;
    bool                          _report_merge_stats;
    bool                          _started;
    bool                          _stop;
    MergeCounts                   _prev_merge_counts;

    void report();
    void run_report_loop(std::chrono::milliseconds interval);
public:
    BmNodeStatsReporter(BmCluster& cluster, bool report_merge_stats);
    ~BmNodeStatsReporter();
    void start(std::chrono::milliseconds interval);
    void stop();
    void report_now();
    std::chrono::time_point<std::chrono::steady_clock> get_change_time() const noexcept { return _change_time; }
};

}
