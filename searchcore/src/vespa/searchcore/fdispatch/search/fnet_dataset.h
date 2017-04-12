// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchcore/fdispatch/search/plain_dataset.h>


class FastS_FNET_DataSet : public FastS_PlainDataSet
{
private:
    FastS_FNET_DataSet(const FastS_FNET_DataSet &);
    FastS_FNET_DataSet& operator=(const FastS_FNET_DataSet &);

public:

    //----------------------------------------------------------------
    // class used to schedule periodic dataset pinging
    //----------------------------------------------------------------

    class PingTask : public FNET_Task
    {
    private:
        PingTask(const PingTask &);
        PingTask& operator=(const PingTask &);

        FastS_FNET_DataSet *_dataset;
        double              _delay;

    public:
        PingTask(FNET_Scheduler *scheduler,
                 FastS_FNET_DataSet *dataset,
                 double delay)
            : FNET_Task(scheduler),
              _dataset(dataset),
              _delay(delay)
        {}
        void PerformTask() override;
    };


private:
    FNET_Transport *_transport;
    PingTask        _pingTask;
    uint64_t        _failedRowsBitmask;

public:
    FastS_FNET_DataSet(FNET_Transport *transport,
                       FNET_Scheduler *scheduler,
                       FastS_AppContext *appCtx,
                       FastS_DataSetDesc *desc);
    virtual ~FastS_FNET_DataSet();

    FNET_Transport *GetTransport() { return _transport; }

    // typesafe down-cast
    virtual FastS_FNET_DataSet *GetFNETDataSet() override { return this; }

    // common dataset API
    virtual bool AddEngine(FastS_EngineDesc *desc) override;
    virtual void ConfigDone(FastS_DataSetCollection *) override;
    virtual void ScheduleCheckBad() override;
    virtual FastS_ISearch *CreateSearch(FastS_DataSetCollection *dsc,
                                        FastS_TimeKeeper *timeKeeper,
                                        bool async) override;
    virtual void Free() override;

    bool isGoodRow(uint32_t rowId);
};
