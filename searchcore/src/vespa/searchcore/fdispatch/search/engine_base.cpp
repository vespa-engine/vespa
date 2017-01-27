// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "engine_base.h"
#include "configdesc.h"
#include "plain_dataset.h"

#include <vespa/log/log.h>
LOG_SETUP(".search.engine_base");

//---------------------------------------------------------------------------

FastS_EngineBase::stats_t::stats_t()
    : _fliptime(),
      _floptime(),
      _slowQueryCnt(0),
      _slowDocsumCnt(0),
      _slowQuerySecs(0.0),
      _slowDocsumSecs(0.0),
      _queueLenSampleAcc(0),
      _queueLenSampleCnt(0),
      _activecntSampleAcc(0),
      _activecntSampleCnt(0),
      _queueLenAcc(0.0),
      _activecntAcc(0.0),
      _queueLenIdx(0),
      _queueLenValid(0)
{
    uint32_t i;

    _fliptime.SetNow();
    _floptime.SetNow();
    for (i = 0; i < _queuestatsize; i++) {
        _queueLens[i]._queueLen  = 0.0;
        _queueLens[i]._activecnt = 0.0;
    }
}

//---------------------------------------------------------------------------

FastS_EngineBase::reported_t::reported_t()
    : _queueLen(0),
      _dispatchers(0),
      _mld(false),
      _reportedPartID(FastS_NoID32()),
      _actNodes(0),
      _maxNodes(0),
      _actParts(0),
      _maxParts(0),
      _activeDocs(),
      _docstamp(FastS_EngineBase::NoDocStamp())
{
    _activeDocs.valid = true;
}


FastS_EngineBase::reported_t::~reported_t(void)
{
}

//---------------------------------------------------------------------------

FastS_EngineBase::config_t::config_t(FastS_EngineDesc *desc)
    : _name(NULL),
      _unitrefcost(desc->GetUnitRefCost()),
      _confPartID(desc->GetConfPartID()),
      _confRowID(desc->GetConfRowID()),
      _confPartIDOverrides(desc->GetConfPartIDOverrides())
{
    _name = strdup(desc->GetName());
    FastS_assert(_name != NULL);
}


FastS_EngineBase::config_t::~config_t()
{
    free(_name);
}

//---------------------------------------------------------------------------

FastS_EngineBase::FastS_EngineBase(FastS_EngineDesc *desc,
                                   FastS_PlainDataSet *dataset)
    : _stats(),
      _reported(),
      _config(desc),
      _isUp(false),
      _badness(BAD_NOT),
      _partid(FastS_NoID32()),
      _totalrefcost(0),
      _activecnt(0),
      _dataset(dataset),
      _nextds(NULL),
      _prevpart(NULL),
      _nextpart(NULL)
{
    FastS_assert(_dataset != NULL);
}


FastS_EngineBase::~FastS_EngineBase()
{
    FastS_assert(_nextds   == NULL);
    FastS_assert(_prevpart == NULL);
    FastS_assert(_nextpart == NULL);
    FastS_assert(_totalrefcost == 0);
    FastS_assert(_activecnt == 0);
}


void
FastS_EngineBase::SlowQuery(double limit, double secs, bool silent)
{
    LockEngine();
    _stats._slowQueryCnt++;
    _stats._slowQuerySecs += secs;
    UnlockEngine();
    if (!silent)
        LOG(warning,
            "engine %s query slow by %.3fs + %.3fs",
            _config._name, limit, secs);
}


void
FastS_EngineBase::SlowDocsum(double limit, double secs)
{
    LockEngine();
    _stats._slowDocsumCnt++;
    _stats._slowDocsumSecs += secs;
    UnlockEngine();
    LOG(warning,
        "engine %s docsum slow by %.3fs + %.3fs",
        _config._name, limit, secs);
}


void
FastS_EngineBase::AddCost()
{
    _totalrefcost += _config._unitrefcost;
    ++_activecnt;
}


void
FastS_EngineBase::SubCost()
{
    FastS_assert(_totalrefcost >= _config._unitrefcost);
    _totalrefcost -= _config._unitrefcost;
    FastS_assert(_activecnt >= 1);
    --_activecnt;
}


void
FastS_EngineBase::SaveQueueLen_NoLock(uint32_t queueLen, uint32_t dispatchers)
{
    _reported._queueLen = queueLen;
    _reported._dispatchers = dispatchers;
    _stats._queueLenSampleAcc += queueLen;
    _stats._queueLenSampleCnt++;
    _stats._activecntSampleAcc += _activecnt;
    _stats._activecntSampleCnt++;
}


void
FastS_EngineBase::SampleQueueLens()
{
    double queueLen;
    double activecnt;

    LockEngine();
    if (_stats._queueLenSampleCnt > 0)
        queueLen = (double) _stats._queueLenSampleAcc / (double) _stats._queueLenSampleCnt;
    else
        queueLen = 0;
    if (_stats._activecntSampleCnt > 0)
        activecnt = (double) _stats._activecntSampleAcc / (double) _stats._activecntSampleCnt;
    else
        activecnt = 0;

    _stats._queueLenSampleAcc = 0;
    _stats._queueLenSampleCnt = 0;
    _stats._activecntSampleAcc = 0;
    _stats._activecntSampleCnt = 0;

    _stats._queueLenAcc -= _stats._queueLens[_stats._queueLenIdx]._queueLen;
    _stats._queueLens[_stats._queueLenIdx]._queueLen = queueLen;
    _stats._queueLenAcc += queueLen;

    _stats._activecntAcc -= _stats._queueLens[_stats._queueLenIdx]._activecnt;
    _stats._queueLens[_stats._queueLenIdx]._activecnt = activecnt;
    _stats._activecntAcc += activecnt;

    _stats._queueLenIdx++;
    if (_stats._queueLenIdx >= _stats._queuestatsize)
        _stats._queueLenIdx = 0;
    if (_stats._queueLenValid < _stats._queuestatsize)
        _stats._queueLenValid++;
    UnlockEngine();
}

void
FastS_EngineBase::UpdateSearchTime(double tnow, double elapsed, bool timedout)
{
    (void) tnow;
    (void) elapsed;
    (void) timedout;
}

void
FastS_EngineBase::MarkBad(uint32_t badness)
{
    bool worse = false;

    LockEngine();
    if (badness > _badness) {
        _badness = badness;
        worse = true;
    }
    UnlockEngine();

    if (worse) {
        if (badness <= BAD_NOT) {
        } else {
            _dataset->ScheduleCheckBad();
        }
    }
}


void
FastS_EngineBase::ClearBad()
{
    LockEngine();
    if (_badness >= BAD_CONFIG) {
        UnlockEngine();
        LOG(warning,
            "engine %s still bad due to illegal config",
            _config._name);
        return;
    }
    _badness = BAD_NOT;
    UnlockEngine();
    HandleClearedBad();
}


void
FastS_EngineBase::HandlePingResponse(uint32_t partid,
                                     time_t docstamp,
                                     bool mld,
                                     uint32_t maxnodes,
                                     uint32_t nodes,
                                     uint32_t maxparts,
                                     uint32_t parts,
                                     PossCount activeDocs)
{
    // ignore really bad nodes
    if (IsRealBad())
        return;

    _reported._reportedPartID = partid;

    // override reported partid ?

    if (_config._confPartIDOverrides && _config._confPartID != FastS_NoID32()) {
        LOG(debug, "Partid(%d) overridden by config(%d)", partid, _config._confPartID);
        partid = _config._confPartID;
    }

    // bad partid ?

    if ((partid != _config._confPartID && _config._confPartID != FastS_NoID32()) ||
        (partid < _dataset->GetFirstPart()) ||
        (partid >= _dataset->GetLastPart()) ||
        (partid >= _dataset->GetFirstPart() + (1 << _dataset->GetPartBits())))
    {
        LOG(warning, "Partid(%d) overridden to %d since it was bad: _confPartID(%d) dataset.first(%d), last(%d), (1 << bits)(%d)", partid, FastS_NoID32(), _config._confPartID, _dataset->GetFirstPart(), _dataset->GetLastPart(), (1 << _dataset->GetPartBits()));
        partid = FastS_NoID32();
    }

    // what happened ?

    bool onlined   = !IsUp();
    bool bigchange = (!onlined &&
                      (partid   != _partid             ||
                       docstamp != _reported._docstamp));
    bool changed   = (!onlined &&
                      (bigchange                       ||
                       mld      != _reported._mld      ||
                       maxnodes != _reported._maxNodes ||
                       nodes    != _reported._actNodes ||
                       maxparts != _reported._maxParts ||
                       activeDocs != _reported._activeDocs ||
                       parts    != _reported._actParts));

    bool partIdChanged = partid != _partid;
    uint32_t oldPartID = _partid;
    // nothing happened ?

#if 0
    LOG(info,
        "HandlePingResponse: "
        "engine %s (partid %d) docstamp %d, "
        "onlined %s, changed %s",
        _config._name,
        static_cast<int>(partid),
        static_cast<int>(docstamp),
        onlined ? "true" : "false",
        changed ? "true" : "false");
#endif
    if (!onlined && !changed)
        return;

    // report stuff

    if (onlined) {
        LOG(debug,
            "Search node %s up, partition %d, docstamp %d",
            _config._name, partid, (uint32_t) docstamp);
    } else if (bigchange) {
        if (partid != _partid) {
            LOG(debug,
                "Search node %s changed partid %u -> %u",
                _config._name, _partid, partid);
        }
        if (docstamp != _reported._docstamp) {
            LOG(debug,
                "Search node %s changed docstamp %u -> %u",
                _config._name,
                (uint32_t)_reported._docstamp,
                (uint32_t)docstamp);
	    if (docstamp == 0) {
	      LOG(warning, "Search node %s (partid %d) went bad (docstamp 0)",
		  _config._name, partid);
	    }
        }
    }

    _dataset->LockDataset();
    if (changed)
        _dataset->LinkOutPart_HasLock(this);

    _partid             = partid;
    if (docstamp != _reported._docstamp) {
        _reported._docstamp = docstamp;
    }
    _reported._mld      = mld;
    _reported._maxNodes = maxnodes;
    _reported._actNodes = nodes;
    _reported._maxParts = maxparts;
    _reported._actParts = parts;
    if (_reported._activeDocs != activeDocs) {
        _dataset->updateActiveDocs_HasLock(GetConfRowID(), activeDocs, _reported._activeDocs);
        _reported._activeDocs = activeDocs;
    }
    _isUp               = true;

    _dataset->LinkInPart_HasLock(this);

    if (partIdChanged) {
        _dataset->EnginePartIDChanged_HasLock(this, oldPartID);
    }
    _dataset->UnlockDataset();
    _dataset->ScheduleCheckTempFail();

    if (onlined) {
        HandleUp();
    }

    // detect flipflop badness

    // NB: fliphistory race with clearbad...

    if (onlined || bigchange) {
        _stats._fliptime.SetNow();
    }
}


void
FastS_EngineBase::HandleLostConnection()
{
    if (IsUp()) {
        _isUp = false;
        _stats._floptime.SetNow();
        LOG(warning, "Search node %s down", _config._name);

        _dataset->LockDataset();
        _dataset->LinkOutPart_HasLock(this);
        PossCount noDocs;
        noDocs.valid = true;
        _dataset->updateActiveDocs_HasLock(GetConfRowID(), noDocs, _reported._activeDocs);
        _reported._activeDocs = noDocs;
        _dataset->UnlockDataset();
        _dataset->ScheduleCheckTempFail();
        HandleDown(); // classic: NotifyVirtualConnsDown
    }
}


void
FastS_EngineBase::HandleNotOnline(int seconds)
{
    LOG(warning, "Search node %s still not up after %d seconds",
        _config._name, seconds);
}


void
FastS_EngineBase::Ping()
{
    SampleQueueLens();
}
