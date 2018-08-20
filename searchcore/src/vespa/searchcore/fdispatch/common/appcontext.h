// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/clock.h>

class FastS_NodeManager;
class FNET_Transport;
class FNET_Scheduler;
class FastS_DataSetCollection;


class FastS_TimeKeeper
{
private:
    vespalib::Clock   _clock;
    FastOS_ThreadPool _thread_pool;

public:
    FastS_TimeKeeper();
    ~FastS_TimeKeeper();

    double GetTime() const { return _clock.getTimeNSAssumeRunning().sec(); }
};


class FastS_AppContext
{
private:
    FastS_TimeKeeper _timeKeeper;
    double           _createTime;

public:
    FastS_AppContext();
    virtual ~FastS_AppContext();

    FastS_TimeKeeper *GetTimeKeeper() { return &_timeKeeper; }

    virtual FastS_NodeManager *GetNodeManager();
    virtual FNET_Transport *GetFNETTransport();
    virtual FNET_Scheduler *GetFNETScheduler();
    virtual FastS_DataSetCollection *GetDataSetCollection();
    virtual FastOS_ThreadPool *GetThreadPool();
    virtual uint32_t getDispatchLevel();
private:
};
