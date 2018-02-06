// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <cstddef>

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
    public:
        Avg() : _value(0.0), _count(0) {}
        void set(double value) { _value = value; _count = 1; }
        double avg() const { return !_count ? 0 : _value / _count; }
        size_t count() const { return _count; }
        void add(const Avg &other) {
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
        size_t _softDoomed;
        Avg    _active_time;
        Avg    _wait_time;
    public:
        Partition()
            : _docsCovered(0),
              _docsMatched(0),
              _docsRanked(0),
              _docsReRanked(0),
              _softDoomed(0),
              _active_time(),
              _wait_time() { }

        Partition &docsCovered(size_t value) { _docsCovered = value; return *this; }
        size_t docsCovered() const { return _docsCovered; }
        Partition &docsMatched(size_t value) { _docsMatched = value; return *this; }
        size_t docsMatched() const { return _docsMatched; }
        Partition &docsRanked(size_t value) { _docsRanked = value; return *this; }
        size_t docsRanked() const { return _docsRanked; }
        Partition &docsReRanked(size_t value) { _docsReRanked = value; return *this; }
        size_t docsReRanked() const { return _docsReRanked; }
        Partition &softDoomed(bool v) { _softDoomed += v ? 1 : 0; return *this; }
        size_t softDoomed() const { return _softDoomed; }

        Partition &active_time(double time_s) { _active_time.set(time_s); return *this; }
        double active_time_avg() const { return _active_time.avg(); }
        size_t active_time_count() const { return _active_time.count(); }
        Partition &wait_time(double time_s) { _wait_time.set(time_s); return *this; }
        double wait_time_avg() const { return _wait_time.avg(); }
        size_t wait_time_count() const { return _wait_time.count(); }

        Partition &add(const Partition &rhs) {
            _docsCovered += rhs.docsCovered();
            _docsMatched += rhs._docsMatched;
            _docsRanked += rhs._docsRanked;
            _docsReRanked += rhs._docsReRanked;
            _softDoomed += rhs._softDoomed;

            _active_time.add(rhs._active_time);
            _wait_time.add(rhs._wait_time);
            return *this;
        }
    };

private:
    size_t                 _queries;
    size_t                 _limited_queries;
    size_t                 _docsCovered;
    size_t                 _docsMatched;
    size_t                 _docsRanked;
    size_t                 _docsReRanked;
    size_t                 _softDoomed;
    double                 _softDoomFactor;
    Avg                    _queryCollateralTime;
    Avg                    _queryLatency;
    Avg                    _matchTime;
    Avg                    _groupingTime;
    Avg                    _rerankTime;
    std::vector<Partition> _partitions;

public:
    MatchingStats(const MatchingStats &) = delete;
    MatchingStats & operator = (const MatchingStats &) = delete;
    MatchingStats(MatchingStats &&) = default;
    MatchingStats & operator =  (MatchingStats &&) = default;
    MatchingStats();
    ~MatchingStats();

    MatchingStats &queries(size_t value) { _queries = value; return *this; }
    size_t queries() const { return _queries; }

    MatchingStats &limited_queries(size_t value) { _limited_queries = value; return *this; }
    size_t limited_queries() const { return _limited_queries; }

    MatchingStats &docsCovered(size_t value) { _docsCovered = value; return *this; }
    size_t docsCovered() const { return _docsCovered; }

    MatchingStats &docsMatched(size_t value) { _docsMatched = value; return *this; }
    size_t docsMatched() const { return _docsMatched; }

    MatchingStats &docsRanked(size_t value) { _docsRanked = value; return *this; }
    size_t docsRanked() const { return _docsRanked; }

    MatchingStats &docsReRanked(size_t value) { _docsReRanked = value; return *this; }
    size_t docsReRanked() const { return _docsReRanked; }

    MatchingStats &softDoomed(size_t value) { _softDoomed = value; return *this; }
    size_t softDoomed() const { return _softDoomed; }
    MatchingStats &softDoomFactor(double value) { _softDoomFactor = value; return *this; }
    double softDoomFactor() const { return _softDoomFactor; }
    MatchingStats &updatesoftDoomFactor(double hardLimit, double softLimit, double duration);

    MatchingStats &queryCollateralTime(double time_s) { _queryCollateralTime.set(time_s); return *this; }
    double queryCollateralTimeAvg() const { return _queryCollateralTime.avg(); }
    size_t queryCollateralTimeCount() const { return _queryCollateralTime.count(); }

    MatchingStats &queryLatency(double time_s) { _queryLatency.set(time_s); return *this; }
    double queryLatencyAvg() const { return _queryLatency.avg(); }
    size_t queryLatencyCount() const { return _queryLatency.count(); }

    MatchingStats &matchTime(double time_s) { _matchTime.set(time_s); return *this; }
    double matchTimeAvg() const { return _matchTime.avg(); }
    size_t matchTimeCount() const { return _matchTime.count(); }

    MatchingStats &groupingTime(double time_s) { _groupingTime.set(time_s); return *this; }
    double groupingTimeAvg() const { return _groupingTime.avg(); }
    size_t groupingTimeCount() const { return _groupingTime.count(); }

    MatchingStats &rerankTime(double time_s) { _rerankTime.set(time_s); return *this; }
    double rerankTimeAvg() const { return _rerankTime.avg(); }
    size_t rerankTimeCount() const { return _rerankTime.count(); }

    // used to merge in stats from each match thread
    MatchingStats &merge_partition(const Partition &partition, size_t id);
    size_t getNumPartitions() const { return _partitions.size(); }
    const Partition &getPartition(size_t index) const { return _partitions[index]; }

    // used to aggregate accross searches (and configurations)
    MatchingStats &add(const MatchingStats &rhs);
};

}
