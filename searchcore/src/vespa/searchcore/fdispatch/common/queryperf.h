// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vespa/vespalib/util/executor.h>

struct FastS_QueryPerf
{
    uint32_t queueLen;
    uint32_t activeCnt;
    uint32_t queryCnt;
    double   queryTime;
    uint32_t dropCnt;
    uint32_t timeoutCnt;

    FastS_QueryPerf();

    /**
     * reset all values except the cached 'old' values. This will
     * prepare the object for reuse logging wise.
     **/
    void reset();
    vespalib::Executor::Task::UP make_log_task();

private:
    uint32_t _lastQueryCnt;
    double   _lastQueryTime;
};

