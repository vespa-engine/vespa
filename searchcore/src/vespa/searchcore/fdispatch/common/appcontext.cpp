// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "appcontext.h"
#include <cassert>

double FastS_TimeKeeper::GetTime() const {
    using clock = std::chrono::steady_clock;
    using seconds = std::chrono::duration<double, std::ratio<1,1>>;
    return std::chrono::duration_cast<seconds>(clock::now().time_since_epoch()).count();
}

//---------------------------------------------------------------------

FastS_AppContext::FastS_AppContext()
    : _timeKeeper(),
      _createTime(_timeKeeper.GetTime())
{
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
