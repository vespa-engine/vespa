// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "appcontext.h"
#include <cassert>

FastS_TimeKeeper::FastS_TimeKeeper()
    : _clock(0.010),
      _thread_pool(128 * 1024)
{
    bool ok = _thread_pool.NewThread(&_clock);
    assert(ok);
    (void) ok;
}


FastS_TimeKeeper::~FastS_TimeKeeper()
{
    _clock.stop();
    _thread_pool.Close();
}

//---------------------------------------------------------------------

FastS_AppContext::FastS_AppContext()
    : _timeKeeper(),
      _createTime()
{
    _createTime = _timeKeeper.GetTime();
}

FastS_AppContext::~FastS_AppContext() = default;

FNET_Transport *
FastS_AppContext::GetFNETTransport()
{
    return nullptr;
}

FNET_Scheduler *
FastS_AppContext::GetFNETScheduler()
{
    return nullptr;
}

FastS_NodeManager *
FastS_AppContext::GetNodeManager()
{
    return nullptr;
}

FastS_DataSetCollection *
FastS_AppContext::GetDataSetCollection()
{
    return nullptr;
}

FastOS_ThreadPool *
FastS_AppContext::GetThreadPool()
{
    return nullptr;
}

uint32_t
FastS_AppContext::getDispatchLevel()
{
    return 0u;
}
