// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "plain_dataset.h"

class FNET_Transport;

class FastS_FNET_DataSet : public FastS_PlainDataSet
{
public:

    //----------------------------------------------------------------
    // class used to schedule periodic dataset pinging
    //----------------------------------------------------------------

    class PingTask : public FNET_Task
    {
    private:
        FastS_FNET_DataSet *_dataset;
        double              _delay;

    public:
        PingTask(const PingTask &) = delete;
        PingTask& operator=(const PingTask &) = delete;
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
    FastS_FNET_DataSet(const FastS_FNET_DataSet &) = delete;
    FastS_FNET_DataSet& operator=(const FastS_FNET_DataSet &) = delete;
    FastS_FNET_DataSet(FNET_Transport *transport,
                       FNET_Scheduler *scheduler,
                       FastS_AppContext *appCtx,
                       FastS_DataSetDesc *desc);
    ~FastS_FNET_DataSet() override;

    FNET_Transport *GetTransport() { return _transport; }

    // typesafe down-cast
    FastS_FNET_DataSet *GetFNETDataSet() override { return this; }

    // common dataset API
    bool AddEngine(FastS_EngineDesc *desc) override;
    void ConfigDone(FastS_DataSetCollection *) override;
    void ScheduleCheckBad() override;
    FastS_ISearch *CreateSearch(FastS_DataSetCollection *dsc,
                                FastS_TimeKeeper *timeKeeper,
                                bool async) override;
    void Free() override;

    bool isGoodRow(uint32_t rowId);
};
