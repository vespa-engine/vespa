// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "plain_dataset.h"
#include "datasetcollection.h"
#include "engine_base.h"
#include "nodemanager.h"
#include <vespa/searchcore/fdispatch/common/search.h>
#include <vespa/vespalib/util/host_name.h>
#include <iomanip>

#include <vespa/log/log.h>
LOG_SETUP(".search.plain_dataset");

//--------------------------------------------------------------------------

static inline int imax(int a, int b) { return (a > b) ? a : b; }

//--------------------------------------------------------------------------

FastS_PartitionMap::Partition::Partition()
    : _engines(nullptr),
      _maxnodesNow(0),
      _maxnodesSinceReload(0),
      _nodes(0),
      _maxpartsNow(0),
      _maxpartsSinceReload(0),
      _parts(0)
{
}


FastS_PartitionMap::Partition::~Partition()
{
    FastS_assert(_engines == nullptr);
    FastS_assert(_nodes == 0);
    FastS_assert(_parts == 0);
}

//--------------------------------------------------------------------------

FastS_PartitionMap::FastS_PartitionMap(FastS_DataSetDesc *desc)
    : _partitions(nullptr),
      _partBits(desc->GetPartBits()),
      _rowBits(desc->GetRowBits()),
      _num_partitions(desc->GetNumParts()),
      _first_partition(desc->GetFirstPart()),
      _minchildparts(desc->GetMinChildParts()),
      _maxNodesDownPerFixedRow(desc->getMaxNodesDownPerFixedRow()),
      _useRoundRobinForFixedRow(desc->useRoundRobinForFixedRow()),
      _childnodes(0),
      _childmaxnodesNow(0),
      _childmaxnodesSinceReload(0),
      _childparts(0),
      _childmaxpartsNow(0),
      _childmaxpartsSinceReload(0),
      _mpp(desc->getMPP()),
      _maxRows(0)
{
    // finalize config settings
    if (_num_partitions > (1U << _partBits)) {
        LOG(error, "Too many partitions %d constrained by partbits %d", _num_partitions, _partBits);
        _num_partitions = (1U << _partBits);
    }

    if (_num_partitions > 0) {
        _partitions = new Partition[_num_partitions];
        FastS_assert(_partitions != nullptr);
    }
    for (FastS_EngineDesc *curr = desc->GetEngineList(); curr != nullptr; curr = curr->GetNext()) {
        _maxRows = std::max(_maxRows, curr->GetConfRowID());
    }
    _numPartitions = std::vector<uint32_t>(getNumRows(), 0);
    for (FastS_EngineDesc *curr = desc->GetEngineList(); curr != nullptr; curr = curr->GetNext()) {
        size_t rowId(curr->GetConfRowID());
        _numPartitions[rowId] = std::max(_numPartitions[rowId], curr->GetConfPartID()+1);
    }
}


FastS_PartitionMap::~FastS_PartitionMap()
{
    delete [] _partitions;
}


void
FastS_PartitionMap::RecalcPartCnt(uint32_t partid)
{
    uint32_t  maxparts = 0;
    uint32_t  parts = 0;
    for (FastS_EngineBase *  engine = _partitions[partid]._engines;
         engine != nullptr; engine = engine->_nextpart) {
        maxparts = imax(engine->_reported._maxParts, maxparts);
        parts = imax(engine->_reported._actParts, parts);
    }
    if (_partitions[partid]._maxpartsNow != maxparts) {
        _childmaxpartsNow += maxparts - _partitions[partid]._maxpartsNow;
        _partitions[partid]._maxpartsNow = maxparts;
        if (_childmaxpartsNow > _childmaxpartsSinceReload)
            _childmaxpartsSinceReload = _childmaxpartsNow;
    }
    if (_partitions[partid]._parts != parts) {
        _childparts += parts - _partitions[partid]._parts;
        _partitions[partid]._parts = parts;
    }
}


void
FastS_PartitionMap::LinkIn(FastS_EngineBase *engine)
{
    uint32_t partid = engine->_partid - _first_partition;

    FastS_assert(partid < GetSize());
    FastS_assert(engine->_nextpart == nullptr);
    FastS_assert(engine->_prevpart == nullptr);
    FastS_PartitionMap::Partition & part = _partitions[partid];
    engine->_nextpart = part._engines;
    if (engine->_nextpart != nullptr)
        engine->_nextpart->_prevpart = engine;
    part._engines = engine;
    part._maxnodesNow += engine->_reported._maxNodes;
    part._maxnodesSinceReload = std::max(part._maxnodesSinceReload, part._maxnodesNow);
    part._nodes += engine->_reported._actNodes;
    _childmaxnodesNow += engine->_reported._maxNodes;
    _childmaxnodesSinceReload = std::max(_childmaxnodesSinceReload, _childmaxnodesNow);
    _childnodes += engine->_reported._actNodes;
    if (part._maxpartsNow <= engine->_reported._maxParts) {
        _childmaxpartsNow += engine->_reported._maxParts
                             - part._maxpartsNow;
        _childmaxpartsSinceReload += std::max(_childmaxpartsSinceReload, _childmaxpartsNow);
        part._maxpartsNow = engine->_reported._maxParts;
    }
    if (part._parts < engine->_reported._actParts) {
        _childparts += engine->_reported._actParts - part._parts;
        part._parts = engine->_reported._actParts;
    }
}


void
FastS_PartitionMap::LinkOut(FastS_EngineBase *engine)
{
    uint32_t partid = engine->_partid - _first_partition;

    FastS_assert(partid < GetSize());
    if (engine->_nextpart != nullptr)
        engine->_nextpart->_prevpart = engine->_prevpart;
    if (engine->_prevpart != nullptr)
        engine->_prevpart->_nextpart = engine->_nextpart;
    if (_partitions[partid]._engines == engine)
        _partitions[partid]._engines = engine->_nextpart;

    _partitions[partid]._maxnodesNow -= engine->_reported._maxNodes;
    _partitions[partid]._nodes  -= engine->_reported._actNodes;
    _childmaxnodesNow -= engine->_reported._maxNodes;
    _childnodes -= engine->_reported._actNodes;
    if (_partitions[partid]._maxpartsNow <= engine->_reported._maxParts ||
        _partitions[partid]._parts <= engine->_reported._actParts)
        RecalcPartCnt(partid);

    engine->_nextpart = nullptr;
    engine->_prevpart = nullptr;
}

//--------------------------------------------------------------------------

FastS_PlainDataSet::MHPN_log_t::MHPN_log_t()
    : _cnt(0),
      _incompleteCnt(0),
      _fuzzyCnt(0)
{
}

//--------------------------------------------------------------------------

void
FastS_PlainDataSet::InsertEngine(FastS_EngineBase *engine)
{
    _enginesArray.push_back(engine);
}

FastS_EngineBase *
FastS_PlainDataSet::ExtractEngine()
{
    if (_enginesArray.size() > 0) {
        FastS_EngineBase *ret = _enginesArray.back();
        _enginesArray.pop_back();
        return ret;
    } else {
        return nullptr;
    }
}

FastS_PlainDataSet::FastS_PlainDataSet(FastS_AppContext *appCtx,
                                       FastS_DataSetDesc *desc)
    : FastS_DataSetBase(appCtx, desc),
      _partMap(desc),
      _stateOfRows(_partMap.getNumRows(), 1.0, desc->GetQueryDistributionMode().getLatencyDecayRate()),
      _MHPN_log(),
      _slowQueryLimitFactor(desc->GetSlowQueryLimitFactor()),
      _slowQueryLimitBias(desc->GetSlowQueryLimitBias()),
      _slowDocsumLimitFactor(desc->GetSlowDocsumLimitFactor()),
      _slowDocsumLimitBias(desc->GetSlowDocsumLimitBias()),
      _monitorInterval(desc->getMonitorInterval()),
      _higherCoverageMaxSearchWait(desc->getHigherCoverageMaxSearchWait()),
      _higherCoverageMinSearchWait(desc->getHigherCoverageMinSearchWait()),
      _higherCoverageBaseSearchWait(desc->getHigherCoverageBaseSearchWait()),
      _minimalSearchCoverage(desc->getMinimalSearchCoverage()),
      _higherCoverageMaxDocSumWait(desc->getHigherCoverageMaxDocSumWait()),
      _higherCoverageMinDocSumWait(desc->getHigherCoverageMinDocSumWait()),
      _higherCoverageBaseDocSumWait(desc->getHigherCoverageBaseDocSumWait()),
      _minimalDocSumCoverage(desc->getMinimalDocSumCoverage()),
      _maxHitsPerNode(desc->GetMaxHitsPerNode()),
      _estimateParts(desc->GetEstimateParts()),
      _estimatePartCutoff(desc->GetEstPartCutoff()),
      _queryDistributionMode(desc->GetQueryDistributionMode()),
      _randState()
{
    uint32_t seed = 0;
    const char *hostname = vespalib::HostName::get().c_str();
    unsigned const char *p = reinterpret_cast<unsigned const char *>(hostname);

    if (p != nullptr) {
        while (*p != '\0') {
            seed = (seed << 7) + *p + (seed >> 25);
            p++;
        }
    }
    seed ^= _createtime.GetSeconds();
    seed ^= _createtime.GetMicroSeconds();
    _randState.srand48(seed);
}


FastS_PlainDataSet::~FastS_PlainDataSet() = default;

void
FastS_PlainDataSet::UpdateMaxHitsPerNodeLog(bool incomplete, bool fuzzy)
{
    auto dsGuard(getDsGuard());
    _MHPN_log._cnt++;
    if (incomplete)
        _MHPN_log._incompleteCnt++;
    if (fuzzy)
        _MHPN_log._fuzzyCnt++;
}


bool
FastS_PlainDataSet::RefCostUseNewEngine(FastS_EngineBase *oldEngine,
                                        FastS_EngineBase *newEngine,
                                        unsigned int *oldCount)
{
    if (oldEngine->_totalrefcost + oldEngine->_config._unitrefcost >
        newEngine->_totalrefcost + newEngine->_config._unitrefcost) {
        *oldCount = 1;
        return true;
    }
    if (oldEngine->_totalrefcost + oldEngine->_config._unitrefcost <
        newEngine->_totalrefcost + newEngine->_config._unitrefcost)
        return false;
    /* Use random generator for tie breaker */
    (*oldCount)++;
    return ((_randState.lrand48() % *oldCount) == 0);
}

void
FastS_PlainDataSet::updateSearchTime(double searchTime, uint32_t rowId)
{
    auto dsGuard(getDsGuard());
    _stateOfRows.updateSearchTime(searchTime, rowId);
}

uint32_t
FastS_PlainDataSet::getRandomWeightedRow() const
{
    return _stateOfRows.getRandomWeightedRow();
}


bool
FastS_PlainDataSet::UseNewEngine(FastS_EngineBase *oldEngine,
                                 FastS_EngineBase *newEngine,
                                 unsigned int *oldCount)
{
    /*
     * If old engine has used _indexSwitchMinSearchGrace seconds
     * of grace period then select new engine if it has used
     * less grace period.
     */
    if (!EngineDocStampOK(oldEngine->_reported._docstamp) &&
        (EngineDocStampOK(newEngine->_reported._docstamp)))
    {
        *oldCount = 1;
        return true;
    }

    /*
     * If new engine has used _indexSwitchMinSearchGrace seconds
     * of grace period then select old engine if it has used
     * less grace period.
     */
    if (!EngineDocStampOK(newEngine->_reported._docstamp) &&
        (EngineDocStampOK(oldEngine->_reported._docstamp)))
    {
        return false;
    }

    return RefCostUseNewEngine(oldEngine, newEngine, oldCount);
}

FastS_EngineBase *
FastS_PlainDataSet::getPartition(const std::unique_lock<std::mutex> &dsGuard, uint32_t partindex, uint32_t rowid)
{
    (void) dsGuard;
    FastS_EngineBase*  ret = nullptr;

    if (IsValidPartIndex_HasLock(partindex)) {
        for (FastS_EngineBase* iter = _partMap._partitions[partindex]._engines;
             iter != nullptr && ret == nullptr;
             iter = iter->_nextpart) {

            // NB: cost race condition

            if (!iter->IsRealBad() &&
                EngineDocStampOK(iter->_reported._docstamp) &&
                iter->_config._confRowID == rowid) {
                ret = iter;
            }
        }
    }

    if (ret != nullptr) {
        ret->AddCost();
    }
    return ret;
}

size_t
FastS_PlainDataSet::countNodesUpInRow_HasLock(uint32_t rowid)
{
    size_t count = 0;
    const size_t numParts = _partMap.GetSize();
    for (size_t partindex = 0; partindex < numParts; ++partindex) {
        for (FastS_EngineBase* iter = _partMap._partitions[partindex]._engines;
             iter != nullptr;
             iter = iter->_nextpart)
        {
            if (!iter->IsRealBad() &&
                EngineDocStampOK(iter->_reported._docstamp) &&
                iter->_config._confRowID == rowid)
            {
                ++count;
                break;
            }
        }
    }
    return count;
}

FastS_EngineBase *
FastS_PlainDataSet::getPartition(const std::unique_lock<std::mutex> &dsGuard, uint32_t partindex)
{
    (void) dsGuard;
    FastS_EngineBase*  ret = nullptr;
    unsigned int oldCount = 1;
    unsigned int engineCount = 0;

    if (IsValidPartIndex_HasLock(partindex)) {
        for (FastS_EngineBase* iter = _partMap._partitions[partindex]._engines;
             iter != nullptr;
             iter = iter->_nextpart) {

            // NB: cost race condition

            if (!iter->IsRealBad() &&
                (iter->_config._unitrefcost > 0) &&
                EngineDocStampOK(iter->_reported._docstamp))
            {
                engineCount++;
                if (ret == nullptr || UseNewEngine(ret, iter, &oldCount))
                    ret = iter;
            }
        }
    }

    if (engineCount < getMPP()) {
        ret = nullptr;
    }
    if (ret != nullptr) {
        ret->AddCost();
    }
    return ret;
}

FastS_EngineBase *
FastS_PlainDataSet::getPartitionMLD(const std::unique_lock<std::mutex> &dsGuard, uint32_t partindex, bool mld)
{
    (void) dsGuard;
    FastS_EngineBase*  ret = nullptr;
    unsigned int oldCount = 1;
    if (partindex < _partMap._num_partitions) {
        FastS_EngineBase* iter;
        for (iter = _partMap._partitions[partindex]._engines; iter != nullptr; iter = iter->_nextpart) {
            // NB: cost race condition

            if (!iter->IsRealBad() &&
                iter->_reported._mld == mld &&
                (iter->_config._unitrefcost > 0) &&
                EngineDocStampOK(iter->_reported._docstamp) &&
                (ret == nullptr || UseNewEngine(ret, iter, &oldCount)))
            {
                ret = iter;
            }
        }
    } else {
        LOG(error, "Couldn't fetch partition data: Partition ID too big, partindex=%x _partMap._num_partitions=%x", partindex, _partMap._num_partitions);
    }
    if (ret != nullptr) {
        ret->AddCost();
    }
    return ret;
}

FastS_EngineBase *
FastS_PlainDataSet::getPartitionMLD(const std::unique_lock<std::mutex> &dsGuard, uint32_t partindex, bool mld, uint32_t rowid)
{
    (void) dsGuard;
    FastS_EngineBase*  ret = nullptr;
    unsigned int oldCount = 1;

    if (partindex < _partMap._num_partitions) {
        FastS_EngineBase* iter;
        for (iter = _partMap._partitions[partindex]._engines; iter != nullptr; iter = iter->_nextpart) {
            // NB: cost race condition
            if (!iter->IsRealBad() &&
                (iter->_reported._mld == mld) &&
                (iter->_config._confRowID == rowid) &&
                EngineDocStampOK(iter->_reported._docstamp) &&
                (ret == nullptr || UseNewEngine(ret, iter, &oldCount)))
            {
                ret = iter;
            }
        }
    } else {
        LOG(error, "Couldn't fetch partition data: Partition ID too big, partindex=%x _partMap._num_partitions=%x", partindex, _partMap._num_partitions);
    }
    if (ret != nullptr) {
        ret->AddCost();
    }
    return ret;
}

void
FastS_PlainDataSet::LinkInPart_HasLock(FastS_EngineBase *engine)
{
    if (engine->GetPartID() == FastS_NoID32())
        return;

    _partMap.LinkIn(engine);
}


void
FastS_PlainDataSet::LinkOutPart_HasLock(FastS_EngineBase *engine)
{
    if (engine->GetPartID() == FastS_NoID32())
        return;

    _partMap.LinkOut(engine);
}


uint32_t
FastS_PlainDataSet::CalculateQueueLens_HasLock(uint32_t &dispatchnodes)
{
    uint32_t partindex;
    uint32_t equeueLen;
    uint32_t pqueueLen;
    FastS_EngineBase *eng;
    uint32_t pdispatchnodes;
    uint32_t dupnodes;

    uint32_t queueLen = 0;
    dispatchnodes = 1;
    for (partindex = 0; partindex < _partMap._num_partitions ; partindex++) {
        eng = _partMap._partitions[partindex]._engines;
        if (eng != nullptr) {
            pqueueLen = eng->GetQueueLen();
            pdispatchnodes = eng->GetDispatchers();
            dupnodes = 1;
            eng = eng->_nextpart;
            while (eng != nullptr) {
                equeueLen = eng->GetQueueLen();
                if (equeueLen < pqueueLen)
                    pqueueLen = equeueLen;
                pdispatchnodes += eng->GetDispatchers();
                dupnodes++;
                eng = eng->_nextpart;
            }
            if (pqueueLen > queueLen)
                queueLen = pqueueLen;
            if (dispatchnodes * dupnodes < pdispatchnodes)
                dispatchnodes = pdispatchnodes / dupnodes;
        }
    }
    return queueLen;
}

namespace {
struct CheckReady {
    bool allReady;
    CheckReady() : allReady(true) {}

    inline void operator()(FastS_EngineBase* engine) {
        allReady &= engine->IsReady();
    }
};

} //anonymous namespace

bool
FastS_PlainDataSet::AreEnginesReady()
{

    // We don't need to lock things here, since the engine list
    // is non-mutable during datasetcollection lifetime.
    return ForEachEngine( CheckReady() ).allReady;
}

void
FastS_PlainDataSet::Ping()
{
    for (FastS_EngineBase* engine : _enginesArray) {
        engine->Ping();
    }
}


ChildInfo
FastS_PlainDataSet::getChildInfo() const
{
    ChildInfo r;
    r.maxNodes    = _partMap._childmaxnodesSinceReload;
    r.activeNodes = _partMap._childnodes;
    r.maxParts    = _partMap._childmaxpartsSinceReload;
    r.activeParts = _partMap._childparts;
    r.activeDocs  = getActiveDocs();
    return r;
}

bool
FastS_PlainDataSet::IsValidPartIndex_HasLock(uint32_t partindex) {
    if (partindex < _partMap._num_partitions) {
        return true;
    } else {
        LOG(error, "Couldn't fetch partition data: Partition ID too big, partindex=%x _partMap._num_partitions=%x", partindex, _partMap._num_partitions);
        return false;
    }
}
