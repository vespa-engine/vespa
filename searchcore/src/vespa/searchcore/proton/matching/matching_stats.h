// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <cstddef>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>
#include <vespa/vespalib/objects/objectvisitor.h>

namespace proton::matching {

/**
 * Statistics for the matching pipeline. Used for internal aggregation
 * before inserting numbers into the metrics framework. The values
 * produced by a single search are set on a single object. Values are
 * aggregated by adding objects together.
 **/
struct MatchingStats
{
private:
    class Avg {
        double _value;
        size_t _count;
        double _min;
        double _max;
    public:
        Avg() noexcept : _value(0.0), _count(0), _min(0.0), _max(0.0) {}
        Avg & set(double value) noexcept {
            _value = value;
            _count = 1;
            _min = value;
            _max = value;
            return *this;
        }
        double avg() const noexcept {
            return (_count > 0) ? (_value / _count) : 0;
        }
        size_t count() const noexcept { return _count; }
        double min() const noexcept { return _min; }
        double max() const noexcept { return _max; }
        void add(const Avg &other) noexcept {
            if (_count == 0) {
                _min = other._min;
                _max = other._max;
            } else if (other._count > 0) {
                _min = std::min(_min, other._min);
                _max = std::max(_max, other._max);
            }
            _value += other._value;
            _count += other._count;
        }
    };

public:

    /**
     * Matching statistics that are tracked separately for each match
     * thread.
     **/
    class Partition {
        size_t _docsCovered;
        size_t _docsMatched;
        size_t _docsRanked;
        size_t _docsReRanked;
        size_t _distances_computed;
        size_t _softDoomed;
        Avg    _doomOvertime;
        Avg    _active_time;
        Avg    _wait_time;
        friend MatchingStats;
    public:
        Partition() noexcept
            : _docsCovered(0),
              _docsMatched(0),
              _docsRanked(0),
              _docsReRanked(0),
              _distances_computed(0),
              _softDoomed(0),
              _doomOvertime(),
              _active_time(),
              _wait_time() { }

        Partition &docsCovered(size_t value) noexcept { _docsCovered = value; return *this; }
        size_t docsCovered() const noexcept { return _docsCovered; }
        Partition &docsMatched(size_t value) noexcept { _docsMatched = value; return *this; }
        size_t docsMatched() const noexcept { return _docsMatched; }
        Partition &docsRanked(size_t value) noexcept { _docsRanked = value; return *this; }
        size_t docsRanked() const noexcept { return _docsRanked; }
        Partition &docsReRanked(size_t value) noexcept { _docsReRanked = value; return *this; }
        size_t docsReRanked() const noexcept { return _docsReRanked; }
        Partition &distances_computed(size_t value) noexcept { _distances_computed = value; return *this; }
        size_t distances_computed() const noexcept { return _distances_computed; }
        Partition &softDoomed(bool v) noexcept { _softDoomed += v ? 1 : 0; return *this; }
        size_t softDoomed() const noexcept { return _softDoomed; }
        Partition & doomOvertime(vespalib::duration overtime) noexcept { _doomOvertime.set(vespalib::to_s(overtime)); return *this; }
        vespalib::duration doomOvertime() const noexcept { return vespalib::from_s(_doomOvertime.max()); }

        Partition &active_time(double time_s) noexcept { _active_time.set(time_s); return *this; }
        double active_time_avg() const noexcept { return _active_time.avg(); }
        size_t active_time_count() const noexcept { return _active_time.count(); }
        double active_time_min() const noexcept { return _active_time.min(); }
        double active_time_max() const noexcept { return _active_time.max(); }
        Partition &wait_time(double time_s) noexcept { _wait_time.set(time_s); return *this; }
        double wait_time_avg() const noexcept { return _wait_time.avg(); }
        size_t wait_time_count() const noexcept { return _wait_time.count(); }
        double wait_time_min() const noexcept { return _wait_time.min(); }
        double wait_time_max() const noexcept { return _wait_time.max(); }

        Partition &add(const Partition &rhs) noexcept {
            _docsCovered += rhs.docsCovered();
            _docsMatched += rhs._docsMatched;
            _docsRanked += rhs._docsRanked;
            _docsReRanked += rhs._docsReRanked;
            _distances_computed += rhs._distances_computed;
            _softDoomed += rhs._softDoomed;

            _doomOvertime.add(rhs._doomOvertime);
            _active_time.add(rhs._active_time);
            _wait_time.add(rhs._wait_time);
            return *this;
        }
    };

    struct Stats {
        size_t                 _queries;
        size_t                 _limited_queries;
        size_t                 _docidSpaceCovered;
        size_t                 _docsMatched;
        size_t                 _docsRanked;
        size_t                 _docsReRanked;
        size_t                 _distances_computed;
        size_t                 _softDoomed;
        Avg                    _doomOvertime;
        Avg                    _querySetupTime;
        Avg                    _queryLatency;
        Avg                    _matchTime;
        Avg                    _groupingTime;
        Avg                    _rerankTime;

        Stats() noexcept
            : _queries(0),
              _limited_queries(0),
              _docidSpaceCovered(0),
              _docsMatched(0),
              _docsRanked(0),
              _docsReRanked(0),
              _distances_computed(0),
              _softDoomed(0),
              _doomOvertime(),
              _querySetupTime(),
              _queryLatency(),
              _matchTime(),
              _groupingTime(),
              _rerankTime() { }

        void add(const Stats &stats) noexcept {
            _queries += stats._queries;
            _limited_queries += stats._limited_queries;
            _docidSpaceCovered += stats._docidSpaceCovered;
            _docsMatched += stats._docsMatched;
            _docsRanked += stats._docsRanked;
            _docsReRanked += stats._docsReRanked;
            _distances_computed += stats._distances_computed;
            _softDoomed += stats._softDoomed;

            _doomOvertime.add(stats._doomOvertime);
            _querySetupTime.add(stats._querySetupTime);
            _queryLatency.add(stats._queryLatency);
            _matchTime.add(stats._matchTime);
            _groupingTime.add(stats._groupingTime);
            _rerankTime.add(stats._rerankTime);
        }
    };

private:
    using SoftDoomFactor = vespalib::datastore::AtomicValueWrapper<double>;
    SoftDoomFactor         _softDoomFactor;
    std::vector<Partition> _partitions;

    Stats                  _stats;
    Stats                  _nn_exact_stats;
    Stats                  _nn_approx_stats;

public:
    static constexpr double INITIAL_SOFT_DOOM_FACTOR = 0.5;
    MatchingStats(const MatchingStats &) = delete;
    MatchingStats & operator = (const MatchingStats &) = delete;
    MatchingStats(MatchingStats &&) noexcept = default;
    MatchingStats & operator =  (MatchingStats &&) noexcept = default;
    MatchingStats() noexcept : MatchingStats(INITIAL_SOFT_DOOM_FACTOR) {}
    MatchingStats(double prev_soft_doom_factor) noexcept;
    ~MatchingStats();

    MatchingStats &queries(size_t value) { _stats._queries = value; return *this; }
    size_t queries() const { return _stats._queries; }

    MatchingStats &limited_queries(size_t value) { _stats._limited_queries = value; return *this; }
    size_t limited_queries() const { return _stats._limited_queries; }

    MatchingStats &docidSpaceCovered(size_t value) { _stats._docidSpaceCovered = value; return *this; }
    size_t docidSpaceCovered() const { return _stats._docidSpaceCovered; }

    MatchingStats &docsMatched(size_t value) { _stats._docsMatched = value; return *this; }
    size_t docsMatched() const { return _stats._docsMatched; }

    MatchingStats &docsRanked(size_t value) { _stats._docsRanked = value; return *this; }
    size_t docsRanked() const { return _stats._docsRanked; }

    MatchingStats &docsReRanked(size_t value) { _stats._docsReRanked = value; return *this; }
    size_t docsReRanked() const { return _stats._docsReRanked; }

    MatchingStats &distances_computed(size_t value) { _stats._distances_computed = value; return *this; }
    size_t distances_computed() const { return _stats._distances_computed; }

    MatchingStats &softDoomed(size_t value) { _stats._softDoomed = value; return *this; }
    size_t softDoomed() const { return _stats._softDoomed; }

    vespalib::duration doomOvertime() const { return vespalib::from_s(_stats._doomOvertime.max()); }

    MatchingStats &softDoomFactor(double value) { _softDoomFactor.store_relaxed(value); return *this; }
    double softDoomFactor() const { return _softDoomFactor.load_relaxed(); }
    MatchingStats &updatesoftDoomFactor(vespalib::duration hardLimit, vespalib::duration softLimit, vespalib::duration duration);

    MatchingStats &querySetupTime(double time_s) { _stats._querySetupTime.set(time_s); return *this; }
    double querySetupTimeAvg() const { return _stats._querySetupTime.avg(); }
    size_t querySetupTimeCount() const { return _stats._querySetupTime.count(); }
    double querySetupTimeMin() const { return _stats._querySetupTime.min(); }
    double querySetupTimeMax() const { return _stats._querySetupTime.max(); }

    MatchingStats &queryLatency(double time_s) { _stats._queryLatency.set(time_s); return *this; }
    double queryLatencyAvg() const { return _stats._queryLatency.avg(); }
    size_t queryLatencyCount() const { return _stats._queryLatency.count(); }
    double queryLatencyMin() const { return _stats._queryLatency.min(); }
    double queryLatencyMax() const { return _stats._queryLatency.max(); }

    MatchingStats &matchTime(double time_s) { _stats._matchTime.set(time_s); return *this; }
    double matchTimeAvg() const { return _stats._matchTime.avg(); }
    size_t matchTimeCount() const { return _stats._matchTime.count(); }
    double matchTimeMin() const { return _stats._matchTime.min(); }
    double matchTimeMax() const { return _stats._matchTime.max(); }

    MatchingStats &groupingTime(double time_s) { _stats._groupingTime.set(time_s); return *this; }
    double groupingTimeAvg() const { return _stats._groupingTime.avg(); }
    size_t groupingTimeCount() const { return _stats._groupingTime.count(); }
    double groupingTimeMin() const { return _stats._groupingTime.min(); }
    double groupingTimeMax() const { return _stats._groupingTime.max(); }

    MatchingStats &rerankTime(double time_s) { _stats._rerankTime.set(time_s); return *this; }
    double rerankTimeAvg() const { return _stats._rerankTime.avg(); }
    size_t rerankTimeCount() const { return _stats._rerankTime.count(); }
    double rerankTimeMin() const { return _stats._rerankTime.min(); }
    double rerankTimeMax() const { return _stats._rerankTime.max(); }

    // used to merge in stats from each match thread
    MatchingStats &merge_partition(const Partition &partition, size_t id);
    size_t getNumPartitions() const { return _partitions.size(); }
    const Partition &getPartition(size_t index) const { return _partitions[index]; }

    // used to aggregate accross searches (and configurations)
    MatchingStats &add(const MatchingStats &rhs) noexcept;

    void add_to_nn_exact_stats() noexcept { _nn_exact_stats.add(_stats); }
    void add_to_nn_approx_stats() noexcept { _nn_approx_stats.add(_stats); }
};

class MatchingStatsCollector : public vespalib::ObjectVisitor {
private:
    size_t _distances_computed;
    bool _approximate_nearest_neighbor_seen;
    bool _exact_nearest_neighbor_seen;

public:
    MatchingStatsCollector()
        : _distances_computed(0),
          _approximate_nearest_neighbor_seen(false),
          _exact_nearest_neighbor_seen(false) {
    }
    ~MatchingStatsCollector() override = default;

    size_t get_distances_computed() const noexcept { return _distances_computed; }
    bool approximate_nearest_neighbor_seen() const noexcept { return _approximate_nearest_neighbor_seen; }
    bool exact_nearest_neighbor_seen() const noexcept { return _exact_nearest_neighbor_seen; }

    void openStruct(std::string_view name, std::string_view type) override {
        (void) name;
        (void) type;
    }
    void closeStruct() override {
    }
    void visitBool(std::string_view name, bool value) override {
        (void) name;
        (void) value;
    }
    void visitInt(std::string_view name, int64_t value) override {
        std::string namestr(name);
        if (name == "distances_computed") {
            _distances_computed += value;
        }
    }
    void visitFloat(std::string_view name, double value) override {
        (void) name;
        (void) value;
    }
    void visitString(std::string_view name, std::string_view value) override {
        if (name == "algorithm") {
            if (value == "exact" || value == "exact_fallback") {
                _exact_nearest_neighbor_seen = true;
            }
            if (value == "index top k") {
                _approximate_nearest_neighbor_seen = true;
            }
            if (value == "index top k using filter") {
                _approximate_nearest_neighbor_seen = true;
            }
        }
    }
    void visitNull(std::string_view name) override {
        (void) name;
    }
    void visitNotImplemented() override {

    }
};

}
