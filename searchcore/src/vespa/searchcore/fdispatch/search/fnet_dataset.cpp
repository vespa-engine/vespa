// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fnet_dataset.h"
#include "fnet_engine.h"
#include "fnet_search.h"
#include "datasetcollection.h"

#include <vespa/log/log.h>
LOG_SETUP(".search.fnet_dataset");

//--------------------------------------------------------------------------

void
FastS_FNET_DataSet::PingTask::PerformTask()
{
    _dataset->Ping();
    Schedule(_delay);
}

//--------------------------------------------------------------------------

FastS_FNET_DataSet::FastS_FNET_DataSet(FNET_Transport *transport,
                                       FNET_Scheduler *scheduler,
                                       FastS_AppContext *appCtx,
                                       FastS_DataSetDesc *desc)
    : FastS_PlainDataSet(appCtx, desc),
      _transport(transport),
      _pingTask(scheduler, this, getMonitorInterval()),
      _failedRowsBitmask(0)
{
}


FastS_FNET_DataSet::~FastS_FNET_DataSet() = default;

bool
FastS_FNET_DataSet::AddEngine(FastS_EngineDesc *desc)
{
    FastS_FNET_Engine *engine = new FastS_FNET_Engine(desc, this);

    InsertEngine(engine);

    if (desc->IsBad()) {
        engine->MarkBad(FastS_EngineBase::BAD_CONFIG);
    }
    return true;
}


namespace {
struct ConnectFNETEngine {
    void operator()(FastS_EngineBase* engine) {
        FastS_FNET_Engine* fnet_engine = engine->GetFNETEngine();
        FastS_assert(fnet_engine != nullptr);
        fnet_engine->ScheduleConnect(0.0);
        fnet_engine->StartWarnTimer();
    }
};
}

void
FastS_FNET_DataSet::ConfigDone(FastS_DataSetCollection *)
{
    ForEachEngine( ConnectFNETEngine() );
    _pingTask.ScheduleNow();
}


void
FastS_FNET_DataSet::ScheduleCheckBad()
{
    _pingTask.ScheduleNow();
}


FastS_ISearch *
FastS_FNET_DataSet::CreateSearch(FastS_DataSetCollection *dsc,
                                 FastS_TimeKeeper *timeKeeper,
                                 bool async)
{
    return (async)
        ? (FastS_ISearch *) new FastS_FNET_Search(dsc, this, timeKeeper)
        : (FastS_ISearch *) new FastS_Sync_FNET_Search(dsc, this, timeKeeper);
}


void
FastS_FNET_DataSet::Free()
{
    _pingTask.Kill();

    for (FastS_EngineBase *engine = ExtractEngine();
         engine != nullptr; engine = ExtractEngine())
    {
        FastS_assert(engine->GetFNETEngine() != nullptr);
        delete engine;
    }

    delete this;
}

bool
FastS_FNET_DataSet::isGoodRow(uint32_t rowId)
{
    auto dsGuard(getDsGuard());
    uint64_t rowBit = 1ul << rowId;
    bool wasBad = ((_failedRowsBitmask & rowBit) != 0);
    bool isBad = false;
    uint64_t candDocs = _stateOfRows.getRowState(rowId).activeDocs();
    // demand: (candidate row active docs >= p% of average active docs)
    // where p = min activedocs coverage
    double p = _queryDistributionMode.getMinActivedocsCoverage() / 100.0;
    p = std::min(p, 0.999); // max demand: 99.9 %
    uint64_t restDocs = _stateOfRows.sumActiveDocs() - candDocs;
    uint64_t restRows = _stateOfRows.numRowStates() - 1;
    double restAvg = (restRows > 0) ? (restDocs / (double)restRows) : 0;
    if (_stateOfRows.activeDocsValid() && (candDocs < (p * restAvg))) {
        isBad = true;
        if (!wasBad) {
            _failedRowsBitmask |= rowBit;
            LOG(warning, "Not enough active docs in row %d (only %lu docs, average is %g)",
                rowId, candDocs, restAvg);
        }
    }
    size_t nodesUp = countNodesUpInRow_HasLock(rowId);
    size_t configuredParts = getNumPartitions(rowId);
    size_t nodesAllowedDown =
        getMaxNodesDownPerFixedRow() +
        (configuredParts*(100.0 - getMinGroupCoverage()))/100.0;
    if (nodesUp + nodesAllowedDown < configuredParts) {
        isBad = true;
        if (!wasBad) {
            _failedRowsBitmask |= rowBit;
            LOG(warning, "Coverage of row %d is only %ld/%ld (requires %ld)",
                rowId, nodesUp, configuredParts, configuredParts-nodesAllowedDown);
        }
    }
    if (wasBad && !isBad) {
        _failedRowsBitmask &= ~rowBit;
        LOG(info, "Row %d is now good again (%lu/%g active docs, coverage %ld/%ld)",
            rowId, candDocs, restAvg, nodesUp, configuredParts);
    }
    return !isBad;
}
