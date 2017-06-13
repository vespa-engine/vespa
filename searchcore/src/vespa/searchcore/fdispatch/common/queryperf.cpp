// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryperf.h"

#include <vespa/log/log.h>
LOG_SETUP(".queryperf");

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

void
FastS_QueryPerf::log()
{
    EV_VALUE("queued_queries",   queueLen);
    EV_VALUE("active_queries",   activeCnt);
    EV_COUNT("queries",          queryCnt);
    EV_COUNT("dropped_queries",  dropCnt);
    EV_COUNT("timedout_queries", timeoutCnt);
    if (queryCnt > _lastQueryCnt) {
        double avgQueryTime = (queryTime - _lastQueryTime)
                              / ((double)(queryCnt - _lastQueryCnt));
        EV_VALUE("query_eval_time_avg_s", avgQueryTime);
    }
    _lastQueryCnt  = queryCnt;
    _lastQueryTime = queryTime;
}
