// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dataset_base.h"
#include "configdesc.h"
#include "datasetcollection.h"
#include "engine_base.h"
#include "nodemanager.h"

//--------------------------------------------------------------------------

FastS_DataSetBase::total_t::total_t()
    : _estimates(0),
      _nTimedOut(0),
      _nOverload(0),
      _normalTimeStat()
{
    for (uint32_t i = 0; i < _timestatslots; i++)
        _timestats[i] = 0;
}

//--------------------------------------------------------------------------

FastS_DataSetBase::overload_t::overload_t(FastS_DataSetDesc *desc)
    : _drainRate(desc->GetQueueDrainRate()),
      _drainMax(desc->GetQueueMaxDrain()),
      _minouractive(desc->GetMinOurActive()),
      _maxouractive(desc->GetMaxOurActive()),
      _cutoffouractive(desc->GetCutoffOurActive()),
      _minestactive(desc->GetMinEstActive()),
      _maxestactive(desc->GetMaxEstActive()),
      _cutoffestactive(desc->GetCutoffEstActive())
{
}

//--------------------------------------------------------------------------

FastS_DataSetBase::queryQueue_t::queryQueue_t(FastS_DataSetDesc *desc)
    : _head(nullptr),
      _tail(nullptr),
      _queueLen(0),
      _active(0),
      _drainAllowed(0.0),
      _drainStamp(0.0),
      _overload(desc)
{
}


FastS_DataSetBase::queryQueue_t::~queryQueue_t()
{
    FastS_assert(_active == 0);
}


void
FastS_DataSetBase::queryQueue_t::QueueTail(queryQueued_t *newqueued)
{
    FastS_assert(newqueued->_next == nullptr &&
                 _head != newqueued &&
                 _tail != newqueued);
    if (_tail != nullptr)
        _tail->_next = newqueued;
    else
        _head = newqueued;
    _tail = newqueued;
    _queueLen++;
}


void
FastS_DataSetBase::queryQueue_t::DeQueueHead()
{
    queryQueued_t *queued = _head;
    FastS_assert(_queueLen > 0);
    FastS_assert(queued->_next != nullptr || _tail == queued);
    _head = queued->_next;
    if (queued->_next == nullptr)
        _tail = nullptr;
    queued->_next = nullptr;
    _queueLen--;
}

//--------------------------------------------------------------------------

FastS_DataSetBase::FastS_DataSetBase(FastS_AppContext *appCtx,
                                     FastS_DataSetDesc *desc)
    : _appCtx(appCtx),
      _lock(),
      _createtime(),
      _queryQueue(desc),
      _total(),
      _id(desc->GetID()),
      _unitrefcost(desc->GetUnitRefCost()),
      _totalrefcost(0),
      _mldDocStamp(0u),
      _searchableCopies(desc->getSearchableCopies())
{
    _createtime.SetNow();
}


FastS_DataSetBase::~FastS_DataSetBase()
{
    FastS_assert(_totalrefcost == 0);
}

void
FastS_DataSetBase::ScheduleCheckTempFail()
{
    _appCtx->GetNodeManager()->ScheduleCheckTempFail(_id);
}


void
FastS_DataSetBase::DeQueueHeadWakeup_HasLock()
{
    queryQueued_t *queued;
    queued = _queryQueue.GetFirst();
    FastS_assert(queued->IsQueued());
    auto queuedGuard(queued->getQueuedGuard());
    //SetNowFromMonitor();
    _queryQueue.DeQueueHead();
    queued->UnmarkQueued();
    FNET_Task *dequeuedTask = queued->getDequeuedTask();
    if (dequeuedTask != nullptr) {
        dequeuedTask->ScheduleNow();
    } else {
        queued->SignalCond();
    }
}


void
FastS_DataSetBase::SetActiveQuery_HasLock()
{
    _queryQueue.SetActiveQuery();
}


void
FastS_DataSetBase::SetActiveQuery()
{
    auto dsGuard(getDsGuard());
    SetActiveQuery_HasLock();
}


void
FastS_DataSetBase::ClearActiveQuery_HasLock(FastS_TimeKeeper *timeKeeper)
{
    FastS_assert(_queryQueue._active > 0);
    _queryQueue.ClearActiveQuery();

    CheckQueryQueue_HasLock(timeKeeper);
}


void
FastS_DataSetBase::ClearActiveQuery(FastS_TimeKeeper *timeKeeper)
{
    auto dsGuard(getDsGuard());
    ClearActiveQuery_HasLock(timeKeeper);
}


void
FastS_DataSetBase::CheckQueryQueue_HasLock(FastS_TimeKeeper *timeKeeper)
{
    queryQueued_t *queued;
    unsigned int active;
    unsigned int estactive;
    uint32_t dispatchnodes;
    double delay;
    double fnow;

    active = _queryQueue.GetActiveQueries();        // active from us
    estactive = CalculateQueueLens_HasLock(dispatchnodes);// active from us and others

    if (dispatchnodes == 0)
        dispatchnodes = 1;

    fnow = timeKeeper->GetTime();
    delay = fnow - _queryQueue._drainStamp;
    if (delay >= 0.0) {
        if (delay > 2.0) {
            delay = 2.0;
            if (_queryQueue._drainStamp == 0.0)
                _queryQueue._drainStamp = fnow;
            else
                _queryQueue._drainStamp += 2.0;
        } else
            _queryQueue._drainStamp = fnow;
    } else
        delay = 0.0;

    _queryQueue._drainAllowed += delay * _queryQueue._overload._drainRate;
    if (_queryQueue._drainAllowed >=
        _queryQueue._overload._drainMax + dispatchnodes - 1)
        _queryQueue._drainAllowed =
            _queryQueue._overload._drainMax + dispatchnodes - 1;

    while (_queryQueue._drainAllowed >= (double) dispatchnodes ||
           active < _queryQueue._overload._minouractive) {
        queued = _queryQueue.GetFirst();
        if (queued == nullptr) {
            return;
        }

        if (active >= _queryQueue._overload._maxouractive)
            return;             // hard limit for how much we queue

        if (active >= _queryQueue._overload._minouractive &&
            estactive >= _queryQueue._overload._minestactive)
            return;

        // Dequeue query, count it active and wakeup thread handling query
        SetActiveQuery_HasLock();
        DeQueueHeadWakeup_HasLock();

        active++;               // one more active from us
        estactive += dispatchnodes;     // Assume other nodes do likewise
        if (_queryQueue._drainAllowed >= (double) dispatchnodes)
            _queryQueue._drainAllowed -= dispatchnodes; // Rate limitation
        else
            _queryQueue._drainAllowed = 0.0;
    }
}


void
FastS_DataSetBase::AbortQueryQueue_HasLock()
{
    queryQueued_t *queued;

    /*
     * Don't allow new queries to be queued.
     * Abort currently queued queries.
     */
    _queryQueue._overload._minouractive = 0;
    _queryQueue._overload._cutoffouractive = 0;
    for (;;) {
        queued = _queryQueue.GetFirst();
        if (queued == nullptr)
            break;
        // Doesn't lock query, but other thread is waiting on queue
        queued->MarkAbort();
        DeQueueHeadWakeup_HasLock();
    }
}


void
FastS_DataSetBase::AddCost()
{
    _totalrefcost += _unitrefcost;
}


void
FastS_DataSetBase::SubCost()
{
    FastS_assert(_totalrefcost >= _unitrefcost);
    _totalrefcost -= _unitrefcost;
}


void
FastS_DataSetBase::UpdateSearchTime(double tnow,
                                    double elapsed, bool timedout)
{
    int slot;
    auto dsGuard(getDsGuard());
    slot = (int) (elapsed * 10);
    if (slot >= _total._timestatslots)
        slot = _total._timestatslots - 1;
    else if (slot < 0)
        slot = 0;
    _total._timestats[slot]++;
    _total._normalTimeStat.Update(tnow, elapsed, timedout);
}


void
FastS_DataSetBase::UpdateEstimateCount()
{
    ++_total._estimates;
}


void
FastS_DataSetBase::CountTimeout()
{
    ++_total._nTimedOut;
}


void
FastS_DataSetBase::addPerformance(FastS_QueryPerf &qp)
{
    FastS_TimeStatTotals totals;
    auto dsGuard(getDsGuard());
    _total._normalTimeStat.AddTotal(&totals);
    qp.queueLen   += _queryQueue.GetQueueLen();
    qp.activeCnt  += _queryQueue.GetActiveQueries();
    qp.queryCnt   += totals._totalCount;
    qp.queryTime  += totals._totalAccTime;
    qp.dropCnt    += _total._nOverload;
    qp.timeoutCnt += _total._nTimedOut;
}


ChildInfo
FastS_DataSetBase::getChildInfo() const
{
    return ChildInfo();
}
