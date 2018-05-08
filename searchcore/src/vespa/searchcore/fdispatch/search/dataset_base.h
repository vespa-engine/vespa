// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "child_info.h"
#include <vespa/searchcore/fdispatch/common/timestat.h>
#include <vespa/searchcore/util/log.h>
#include <atomic>
#include <vespa/fastos/time.h>
#include <mutex>
#include <condition_variable>

class FastS_TimeKeeper;

class FastS_DataSetDesc;
class FastS_EngineDesc;
class FastS_DataSetCollection;
class FastS_ISearch;
class FastS_QueryResult;
class FastS_PlainDataSet;
class FastS_FNET_DataSet;
class FastS_AppContext;
class FastS_QueryPerf;
class FNET_Task;

//---------------------------------------------------------------------------

class FastS_DataSetBase
{
    friend class FastS_DataSetCollection;
public:

    //----------------------------------------------------------------
    // total query stats
    //----------------------------------------------------------------

    class total_t
    {
    public:
        enum {
            _timestatslots = 100
        };
        std::atomic<uint32_t> _estimates;
        std::atomic<uint32_t> _nTimedOut;
        uint32_t _nOverload;
        uint32_t _timestats[_timestatslots];
        FastS_TimeStatHistory _normalTimeStat;
        total_t();
    };

    //----------------------------------------------------------------
    // parameters used by query queue
    //----------------------------------------------------------------

    class overload_t
    {
    public:
        double   _drainRate;       // Queue drain rate
        double   _drainMax;        // Max queue drain at once
        uint32_t _minouractive;    // minimum active requests from us
        uint32_t _maxouractive;    // maximum active requests from us (queue)
        uint32_t _cutoffouractive; // cutoff active requests
        uint32_t _minestactive;    // minimum estimated requests before queueing
        uint32_t _maxestactive;    // maximum estimated requests (start earlydrop)
        uint32_t _cutoffestactive; // cutoff estimated requests  (end earlydrop)

        overload_t(FastS_DataSetDesc *desc);
    };

    //----------------------------------------------------------------
    // class used to wait for a query queue
    //----------------------------------------------------------------

    class queryQueue_t;
    class queryQueued_t
    {
        friend class queryQueue_t;
    private:
        queryQueued_t(const queryQueued_t &);
        queryQueued_t& operator=(const queryQueued_t &);

        std::mutex _queuedLock;
        std::condition_variable _queuedCond;
        queryQueued_t *_next;
        bool _isAborted;
        bool _isQueued;
        FNET_Task *const _deQueuedTask;
    public:
        queryQueued_t(FNET_Task *const deQueuedTask)
            : _queuedLock(),
              _queuedCond(),
              _next(NULL),
              _isAborted(false),
              _isQueued(false),
              _deQueuedTask(deQueuedTask)
        {
        }

        ~queryQueued_t()
        {
            FastS_assert(!_isQueued);
        }
        void Wait() {
            std::unique_lock<std::mutex> queuedGuard(_queuedLock);
            while (_isQueued) {
                _queuedCond.wait(queuedGuard);
            }
        }
        bool IsAborted() const { return _isAborted; }
        void MarkAbort() { _isAborted = true; }
        void MarkQueued() { _isQueued = true; }
        void UnmarkQueued() { _isQueued = false; }
        bool IsQueued() const { return _isQueued; }
        std::unique_lock<std::mutex> getQueuedGuard() { return std::unique_lock<std::mutex>(_queuedLock); }
        void SignalCond() { _queuedCond.notify_one(); }

        FNET_Task *
        getDequeuedTask() const
        {
            return _deQueuedTask;
        }
    };

    //----------------------------------------------------------------
    // per dataset query queue
    //----------------------------------------------------------------

    class queryQueue_t
    {
        friend class FastS_DataSetBase;

    private:
        queryQueue_t(const queryQueue_t &);
        queryQueue_t& operator=(const queryQueue_t &);

        queryQueued_t     *_head;
        queryQueued_t     *_tail;
        unsigned int           _queueLen;
        unsigned int           _active;

    public:
        double                 _drainAllowed; // number of drainable request
        double                 _drainStamp;   // stamp of last drain check
        overload_t             _overload;     // queue parameters

    public:
        queryQueue_t(FastS_DataSetDesc *desc);
        ~queryQueue_t();
        void QueueTail(queryQueued_t *newquery);
        void DeQueueHead();
        unsigned int GetQueueLen() const        { return _queueLen; }
        unsigned int GetActiveQueries() const   { return _active; }
        void SetActiveQuery()                   { _active++; }
        void ClearActiveQuery()                 { _active--; }
        queryQueued_t *GetFirst() const { return _head; }
    };

    //----------------------------------------------------------------

protected:
    FastS_AppContext *_appCtx;
    std::mutex   _lock;
    FastOS_Time  _createtime;
    queryQueue_t _queryQueue;
    total_t      _total;
    uint32_t     _id;
    uint32_t     _unitrefcost;

    // Total cost as seen by referencing objects
    std::atomic<uint32_t>  _totalrefcost;
    uint32_t     _mldDocStamp;
private:
    uint32_t     _searchableCopies;

public:
    FastS_DataSetBase(const FastS_DataSetBase &) = delete;
    FastS_DataSetBase& operator=(const FastS_DataSetBase &) = delete;
    FastS_DataSetBase(FastS_AppContext *appCtx, FastS_DataSetDesc *desc);
    virtual ~FastS_DataSetBase();

    // locking stuff
    //--------------
    std::unique_lock<std::mutex> getDsGuard() { return std::unique_lock<std::mutex>(_lock); }

    // query queue related methods
    //----------------------------
    void SetActiveQuery_HasLock();
    void SetActiveQuery();
    void ClearActiveQuery_HasLock(FastS_TimeKeeper *timeKeeper);
    void ClearActiveQuery(FastS_TimeKeeper *timeKeeper);
    void CheckQueryQueue_HasLock(FastS_TimeKeeper *timeKeeper);
    void AbortQueryQueue_HasLock();

    // common dataset methods
    //-----------------------
    uint32_t GetID() { return _id; }
    double Uptime() { return _createtime.MilliSecsToNow() / 1000.0; }
    FastS_AppContext *GetAppContext() const { return _appCtx; }
    void AddCost();
    void SubCost();
    void UpdateSearchTime(double tnow, double elapsed, bool timedout);
    void UpdateEstimateCount();
    void CountTimeout();
    uint32_t getSearchableCopies() const { return _searchableCopies; }

    void ScheduleCheckTempFail();
    virtual void DeQueueHeadWakeup_HasLock();
    virtual ChildInfo getChildInfo() const;
    uint32_t GetMldDocStamp() const { return _mldDocStamp; }
    void SetMldDocStamp(uint32_t mldDocStamp) { _mldDocStamp = mldDocStamp; }

    // common dataset API
    //-------------------
    virtual uint32_t CalculateQueueLens_HasLock(uint32_t &dispatchnodes) = 0;
    virtual bool AddEngine(FastS_EngineDesc *desc) = 0;
    virtual void ConfigDone(FastS_DataSetCollection *) {}
    virtual void ScheduleCheckBad() {}
    virtual bool AreEnginesReady() = 0;
    virtual FastS_ISearch *CreateSearch(FastS_DataSetCollection *dsc,
                                        FastS_TimeKeeper *timeKeeper,
                                        bool async) = 0;
    virtual void Free() = 0;
    virtual void addPerformance(FastS_QueryPerf &qp);

    // typesafe down-cast
    //-------------------
    virtual FastS_PlainDataSet     *GetPlainDataSet()     { return nullptr; }
    virtual FastS_FNET_DataSet     *GetFNETDataSet()      { return nullptr; }
};

//---------------------------------------------------------------------------

