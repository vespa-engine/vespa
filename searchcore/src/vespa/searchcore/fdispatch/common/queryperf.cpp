// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryperf.h"

#include <vespa/log/log.h>
LOG_SETUP(".queryperf");

namespace {

struct MyLogTask : vespalib::Executor::Task {
    uint32_t queueLen;
    uint32_t activeCnt;
    uint32_t queryCnt;
    uint32_t dropCnt;
    uint32_t timeoutCnt;
    double avgQueryTime;
    MyLogTask(uint32_t queueLen_in,
              uint32_t activeCnt_in,
              uint32_t queryCnt_in,
              uint32_t dropCnt_in,
              uint32_t timeoutCnt_in,
              double avgQueryTime_in)
        : queueLen(queueLen_in),
          activeCnt(activeCnt_in),
          queryCnt(queryCnt_in),
          dropCnt(dropCnt_in),
          timeoutCnt(timeoutCnt_in),
          avgQueryTime(avgQueryTime_in)
    {
    }
    void run() override {
        EV_VALUE("queued_queries",   queueLen);
        EV_VALUE("active_queries",   activeCnt);
        EV_COUNT("queries",          queryCnt);
        EV_COUNT("dropped_queries",  dropCnt);
        EV_COUNT("timedout_queries", timeoutCnt);
        if (avgQueryTime > 0.0) {
            EV_VALUE("query_eval_time_avg_s", avgQueryTime);
        }
    }
};

} // namespace <unnamed>

FastS_QueryPerf::FastS_QueryPerf()
    : queueLen(0),
      activeCnt(0),
      queryCnt(0),
      queryTime(0),
      dropCnt(0),
      timeoutCnt(0),
      _lastQueryCnt(0),
      _lastQueryTime(0.0)
{
}

void
FastS_QueryPerf::reset()
{
    queueLen   = 0;
    activeCnt  = 0;
    queryCnt   = 0;
    queryTime  = 0;
    dropCnt    = 0;
    timeoutCnt = 0;
}

vespalib::Executor::Task::UP
FastS_QueryPerf::make_log_task()
{
    double avgQueryTime = 0.0;
    if (queryCnt > _lastQueryCnt) {
        avgQueryTime = (queryTime - _lastQueryTime)
                       / ((double)(queryCnt - _lastQueryCnt));
    }
    _lastQueryCnt  = queryCnt;
    _lastQueryTime = queryTime;
    return std::make_unique<MyLogTask>(queueLen,
                                       activeCnt,
                                       queryCnt,
                                       dropCnt,
                                       timeoutCnt,
                                       avgQueryTime);
}
