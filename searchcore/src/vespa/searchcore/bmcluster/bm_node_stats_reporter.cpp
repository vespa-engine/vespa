// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_node_stats_reporter.h"
#include "bm_cluster.h"
#include "bm_node_stats.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".bmcluster.bm_node_stats_reporter");


using vespalib::makeLambdaTask;

namespace search::bmcluster {

namespace {

bool steady_buckets_stats(const std::optional<BmBucketsStats> buckets)
{
    if (!buckets.has_value()) {
        return false; // No info available
    }
    auto& value = buckets.value();
    if (!value.get_valid()) {
        return false; // Some information still missing
    }
    return value.get_buckets_pending() == 0u;
}

}

BmNodeStatsReporter::BmNodeStatsReporter(BmCluster &cluster, bool report_merge_stats)
    : _cluster(cluster),
      _executor(1),
      _mutex(),
      _cond(),
      _change_time(),
      _prev_node_stats(),
      _pending_report(1u),
      _report_merge_stats(report_merge_stats),
      _started(false),
      _stop(false)
{
}

BmNodeStatsReporter::~BmNodeStatsReporter()
{
    if (!_stop) {
        stop();
    }
}

void
BmNodeStatsReporter::start(std::chrono::milliseconds interval)
{
    if (!_started) {
        _started = true;
        _executor.execute(makeLambdaTask([this, interval]() { run_report_loop(interval); }));
        std::unique_lock<std::mutex> guard(_mutex);
        _cond.wait(guard, [this]() { return _pending_report == 0u; });
    }
}

void
BmNodeStatsReporter::stop()
{
    {
        std::lock_guard guard(_mutex);
        _stop = true;
        _cond.notify_all();
    }
    _executor.sync();
    _executor.shutdown();
}

void
BmNodeStatsReporter::report_now()
{
    std::unique_lock<std::mutex> guard(_mutex);
    ++_pending_report;
    _cond.notify_all();
    _cond.wait(guard, [this]() { return _pending_report == 0u || _stop; });
}

void
BmNodeStatsReporter::report()
{
    using Width = vespalib::asciistream::Width;
    auto node_stats = _cluster.get_node_stats();
    vespalib::asciistream s;
    s << "nodes stats ";
    BmNodeStats totals;
    for (auto &node : node_stats) {
        auto& document_db = node.get_document_db_stats();
        if (document_db.has_value()) {
            s << Width(10) << document_db.value().get_total_docs();
        } else {
            s << Width(10) << "-";
        }
        totals += node;
    }
    if (totals.get_document_db_stats().has_value()) {
        s << Width(10) << totals.get_document_db_stats().value().get_total_docs();
    } else {
        s << Width(10) << "-";
    }
    auto& total_buckets = totals.get_buckets_stats();
    if (total_buckets.has_value()) {
        s << Width(8) << total_buckets.value().get_buckets_pending();
        if (!total_buckets.value().get_valid()) {
            s << "?";
        }
    } else {
        s << Width(8) << "-";
    }
    vespalib::string ss(s.str());
    LOG(info, "%s", ss.c_str());
    if (_report_merge_stats) {
        s.clear();
        vespalib::asciistream ns;
        s << "merge stats ";
        for (auto& node : node_stats) {
            auto &merges = node.get_merge_stats();
            if (merges.has_value()) {
                ns.clear();
                ns << merges.value().get_active() << "/" << merges.value().get_queued();
                s << Width(10) << ns.str();
            } else {
                s << Width(10) << "-";
            }
        }
        ss = s.str();
        LOG(info, "%s", ss.c_str());
    }
    if (!(node_stats == _prev_node_stats) || !steady_buckets_stats(total_buckets)) {
        _change_time = std::chrono::steady_clock::now();
        _prev_node_stats = node_stats;
    }
}

void
BmNodeStatsReporter::run_report_loop(std::chrono::milliseconds interval)
{
    std::unique_lock<std::mutex> guard(_mutex);
    while (!_stop) {
        uint32_t pending_handled = _pending_report;
        guard.unlock();
        report();
        guard.lock();
        if (pending_handled != 0u) {
            _pending_report -= pending_handled;
            _cond.notify_all();
        }
        if (!_stop && _pending_report == 0u) {
            _cond.wait_for(guard, interval);
        }
    }
}

}
