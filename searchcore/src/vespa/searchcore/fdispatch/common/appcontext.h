// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/vespalib/util/clock.h>
#include <vespa/vespalib/net/lazy_resolver.h>

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
    vespalib::LazyResolver::SP _lazy_resolver;

public:
    FastS_AppContext();
    virtual ~FastS_AppContext();

    FastS_TimeKeeper *GetTimeKeeper() { return &_timeKeeper; }
    vespalib::LazyResolver &get_lazy_resolver() { return *_lazy_resolver; }

    virtual FastS_NodeManager *GetNodeManager();
    virtual FNET_Transport *GetFNETTransport();
    virtual FNET_Scheduler *GetFNETScheduler();
    virtual FastS_DataSetCollection *GetDataSetCollection();
    virtual FastOS_ThreadPool *GetThreadPool();
    virtual void logPerformance();
    virtual uint32_t getDispatchLevel();
private:
};
