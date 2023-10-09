// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "qps_analyzer.h"

namespace vbench {

QpsAnalyzer::QpsAnalyzer(Handler<Request> &next)
    : _next(next),
      _qps(0),
      _samples(0),
      _begin(0),
      _cnt(0)
{
}

void
QpsAnalyzer::handle(Request::UP request)
{
    if (request->status() == Request::STATUS_OK) {
        addEndTime(request->endTime());
    }
    _next.handle(std::move(request));
}

void
QpsAnalyzer::report()
{
    fprintf(stdout, "end qps: %g\n", _qps);
}

void
QpsAnalyzer::addEndTime(double end)
{
    ++_cnt;
    if (end < _begin) {
        _begin = end;
    }
    if ((end - _begin) > 5.0) {
        double newQps = ((double)_cnt) / (end - _begin);
        double factor = (_samples == 0) ? 1.0 : 0.75;
        _qps = (((1 - factor) * _qps) + (factor * newQps));
        ++_samples;
        _begin = end;
        _cnt = 0;
        fprintf(stderr, "qps: %g\n", _qps);
    }
}

} // namespace vbench
