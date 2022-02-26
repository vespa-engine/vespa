// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "analyzer.h"

namespace vbench {

/**
 * Component picking up the latency of successful requests and
 * calculating relevant aggregated values.
 **/
class LatencyAnalyzer : public Analyzer
{
private:
    Handler<Request>    &_next;
    size_t               _cnt;
    double               _min;
    double               _max;
    double               _total;
    std::vector<size_t>  _hist;

    double getN(size_t n) const;
    double getPercentile(double per) const;

public:
    struct Stats {
        double min;
        double avg;
        double max;
        double per50;
        double per95;
        double per99;
        Stats() : min(0), avg(0), max(0), per50(0), per95(0), per99(0) {}
        string toString() const;
    };
    LatencyAnalyzer(Handler<Request> &next);
    ~LatencyAnalyzer() override;
    void handle(Request::UP request) override;
    void report() override;
    void addLatency(double latency);
    Stats getStats() const;
};

} // namespace vbench
