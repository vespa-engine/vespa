// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "latency_analyzer.h"
#include <cmath>

namespace vbench {

double
LatencyAnalyzer::getN(size_t n) const
{
    size_t acc = 0;
    for (size_t i = 0; i < _hist.size(); ++i) {
        acc += _hist[i];
        if (acc > n) {
            return (((double)i) / 1000.0);
        }
    }
    return _max;
}

double
LatencyAnalyzer::getPercentile(double per) const
{
    double target = std::max((((double)(_cnt - 1)) * (per / 100.0)), 0.0);
    size_t before = (size_t)std::floor(target);
    size_t  after = (size_t)std::ceil(target);
    double factor = std::ceil(target) - target;
    return (factor * getN(before) + (1.0 - factor) * getN(after));
}

string
LatencyAnalyzer::Stats::toString() const
{
    string str = "Latency {\n";
    str += strfmt("  min: %g\n", min);
    str += strfmt("  avg: %g\n", avg);
    str += strfmt("  max: %g\n", max);
    str += strfmt("  50%%: %g\n", per50);
    str += strfmt("  95%%: %g\n", per95);
    str += strfmt("  99%%: %g\n", per99);
    str += "}\n";
    return str;
}

LatencyAnalyzer::LatencyAnalyzer(Handler<Request> &next)
    : _next(next),
      _cnt(0),
      _min(0.0),
      _max(0.0),
      _total(0.0),
      _hist(10000, 0)
{
}

LatencyAnalyzer::~LatencyAnalyzer() = default;

void
LatencyAnalyzer::handle(Request::UP request)
{
    if (request->status() == Request::STATUS_OK) {
        addLatency(request->latency());
    }
    _next.handle(std::move(request));
}

void
LatencyAnalyzer::report()
{
    fprintf(stdout, "%s\n", getStats().toString().c_str());
}

void
LatencyAnalyzer::addLatency(double latency)
{
    if (_cnt == 0 || latency < _min) {
        _min = latency;
    }
    if (_cnt == 0 || latency > _max) {
        _max = latency;
    }
    ++_cnt;
    _total += latency;
    size_t idx = (size_t)(latency * 1000.0 + 0.5);
    if (idx < _hist.size()) {
        ++_hist[idx];
    }
}

LatencyAnalyzer::Stats
LatencyAnalyzer::getStats() const
{
    Stats stats;
    stats.min = _min;
    if (_cnt > 0) {
        stats.avg = (_total / (double)_cnt);
    }
    stats.max = _max;
    stats.per50 = getPercentile(50.0);
    stats.per95 = getPercentile(95.0);
    stats.per99 = getPercentile(99.0);
    return stats;
}

} // namespace vbench
