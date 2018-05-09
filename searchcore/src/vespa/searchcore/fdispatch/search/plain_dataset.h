// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <list>

#include "child_info.h"
#include <vespa/searchcore/fdispatch/search/dataset_base.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/searchcore/fdispatch/search/configdesc.h>
#include <vespa/searchcore/fdispatch/search/rowstate.h>
#include <vespa/fnet/task.h>

class FastS_EngineBase;

//----------------------------------------------------------------
// class holding information about a set of partitions
//----------------------------------------------------------------
class FastS_PartitionMap
{
public:

    //----------------------------------------------------------------
    // class holding information about a single partition
    //----------------------------------------------------------------
    class Partition
    {

    public:
        FastS_EngineBase *_engines;
        uint32_t          _maxnodesNow;
        uint32_t          _maxnodesSinceReload;
        uint32_t          _nodes;
        uint32_t          _maxpartsNow;
        uint32_t          _maxpartsSinceReload;
        uint32_t          _parts;

    public:
        Partition();
        ~Partition();
    private:
        Partition(const Partition &);
        Partition& operator=(const Partition &);
    };


public:
    Partition *_partitions;
    uint32_t   _partBits;
    uint32_t   _rowBits;
    uint32_t   _num_partitions;  // Number of partitions (active)
    uint32_t   _first_partition;   // From partitions-file 'firstpart' (active)
    uint32_t   _minchildparts;   // Minimum partitions live to avoid tempfail
    uint32_t   _maxNodesDownPerFixedRow;
    bool       _useRoundRobinForFixedRow;
    uint32_t   _childnodes;
    uint32_t   _childmaxnodesNow;
    uint32_t   _childmaxnodesSinceReload;
    uint32_t   _childparts;
    uint32_t   _childmaxpartsNow;
    uint32_t   _childmaxpartsSinceReload;
    uint32_t   _mpp;        // Number of engines needed per partition

    std::vector<uint32_t> _numPartitions;

public:
    FastS_PartitionMap(FastS_DataSetDesc *desc);
    ~FastS_PartitionMap();

    void RecalcPartCnt(uint32_t partid);
    void LinkIn(FastS_EngineBase *engine);
    void LinkOut(FastS_EngineBase *engine);

    uint32_t GetSize() { return _num_partitions; }

    uint32_t getNumRows() const { return _maxRows + 1; }
    uint32_t getNumPartitions(size_t rowId) { return _numPartitions[rowId]; }
private:
    FastS_PartitionMap(const FastS_PartitionMap &);
    FastS_PartitionMap& operator=(const FastS_PartitionMap &);
    uint32_t   _maxRows;

};

//---------------------------------------------------------------------------

class FastS_PlainDataSet : public FastS_DataSetBase
{
    friend class FastS_NodeManager;

public:

    //----------------------------------------------------------------
    // Max Hits Per Node Stats
    //----------------------------------------------------------------

    class MHPN_log_t
    {
    public:
        uint32_t _cnt;            // # times maxHitsPerNode affected # hits requested
        uint32_t _incompleteCnt;  // # times maxHitsPerNode caused too few hits
        uint32_t _fuzzyCnt;       // # times maxHitsPerNode may have caused wrong hits

        MHPN_log_t();
    };

protected:
    FastS_PartitionMap     _partMap;
    fdispatch::StateOfRows _stateOfRows;
    MHPN_log_t   _MHPN_log;
    double       _slowQueryLimitFactor;
    double       _slowQueryLimitBias;
    double       _slowDocsumLimitFactor;
    double       _slowDocsumLimitBias;
    double       _monitorInterval;
    double       _higherCoverageMaxSearchWait;
    double       _higherCoverageMinSearchWait;
    double       _higherCoverageBaseSearchWait;
    double       _minimalSearchCoverage;
    double       _higherCoverageMaxDocSumWait;
    double       _higherCoverageMinDocSumWait;
    double       _higherCoverageBaseDocSumWait;
    double       _minimalDocSumCoverage;
    uint32_t     _maxHitsPerNode;     // Max hits requested from single node
    uint32_t     _estimateParts;      // number of partitions used for estimate
    uint32_t     _estimatePartCutoff; // First partition not used for estimate

    FastS_DataSetDesc::QueryDistributionMode  _queryDistributionMode;
    //all engines in this dataset
    std::vector<FastS_EngineBase *> _enginesArray;
    search::Rand48 _randState;

    void InsertEngine(FastS_EngineBase *engine);
    FastS_EngineBase *ExtractEngine();
    bool RefCostUseNewEngine(FastS_EngineBase *oldEngine, FastS_EngineBase *newEngine, unsigned int *oldCount);
    bool UseNewEngine(FastS_EngineBase *oldEngine, FastS_EngineBase *newEngine, unsigned int *oldCount);

    bool IsValidPartIndex_HasLock(uint32_t partindex);
public:
    FastS_PlainDataSet(FastS_AppContext *appCtx, FastS_DataSetDesc *desc);
    ~FastS_PlainDataSet() override;

    bool useFixedRowDistribution() const {
        return _queryDistributionMode == FastS_DataSetDesc::QueryDistributionMode::FIXEDROW;
    }
    uint32_t getNumRows() const { return _partMap.getNumRows(); }
    uint32_t getNumPartitions(size_t rowId) { return _partMap.getNumPartitions(rowId); }
    uint32_t GetRowBits() const { return _partMap._rowBits; }
    uint32_t GetPartBits() const { return _partMap._partBits; }
    uint32_t GetFirstPart() const { return _partMap._first_partition; }
    uint32_t GetLastPart() const {
        return _partMap._first_partition + _partMap._num_partitions;
    }
    uint32_t GetPartitions() const { return _partMap._num_partitions; }
    uint32_t GetEstimateParts() const { return _estimateParts; }
    uint32_t GetEstimatePartCutoff() const { return _estimatePartCutoff; }
    uint32_t GetMaxHitsPerNode() const { return _maxHitsPerNode; }
    double GetSlowQueryLimitFactor() const { return _slowQueryLimitFactor; }
    double GetSlowQueryLimitBias() const { return _slowQueryLimitBias; }
    double GetSlowDocsumLimitFactor() const { return _slowDocsumLimitFactor; }
    double GetSlowDocsumLimitBias() const { return _slowDocsumLimitBias; }
    bool GetTempFail() const { return _partMap._childparts < _partMap._minchildparts; }
    void UpdateMaxHitsPerNodeLog(bool incomplete, bool fuzzy);
    uint32_t getMaxNodesDownPerFixedRow() const { return _partMap._maxNodesDownPerFixedRow; }
    uint32_t useRoundRobinForFixedRow() const { return _partMap._useRoundRobinForFixedRow; }
    double getMinGroupCoverage() const { return _queryDistributionMode.getMinGroupCoverage(); }
    void updateSearchTime(double searchTime, uint32_t rowId);
    void updateActiveDocs_HasLock(uint32_t rowId, PossCount newVal, PossCount oldVal) {
        _stateOfRows.updateActiveDocs(rowId, newVal, oldVal);
    }
    PossCount getActiveDocs() const { return _stateOfRows.getActiveDocs(); }
    uint32_t getRandomWeightedRow() const;

    FastS_EngineBase * getPartition(const std::unique_lock<std::mutex> &dsGuard, uint32_t partid);
    FastS_EngineBase * getPartition(const std::unique_lock<std::mutex> &dsGuard, uint32_t partid, uint32_t rowid);

    size_t countNodesUpInRow_HasLock(uint32_t rowid);

    FastS_EngineBase * getPartitionMLD(const std::unique_lock<std::mutex> &dsGuard, uint32_t partid, bool mld);
    FastS_EngineBase * getPartitionMLD(const std::unique_lock<std::mutex> &dsGuard, uint32_t partid, bool mld, uint32_t rowid);

    void LinkInPart_HasLock(FastS_EngineBase *engine);
    void LinkOutPart_HasLock(FastS_EngineBase *engine);

    ChildInfo getChildInfo() const override;

    uint32_t getMPP() const { return _partMap._mpp; }
    double getMonitorInterval() const { return _monitorInterval; }
    double getHigherCoverageMaxSearchWait() const { return _higherCoverageMaxSearchWait; }
    double getHigherCoverageMinSearchWait() const { return _higherCoverageMinSearchWait; }
    double getMinimalSearchCoverage() const { return _minimalSearchCoverage; }
    double getHigherCoverageMaxDocSumWait() const { return _higherCoverageMaxDocSumWait; }
    double getHigherCoverageMinDocSumWait() const { return _higherCoverageMinDocSumWait; }
    double getHigherCoverageBaseDocSumWait() const { return _higherCoverageBaseDocSumWait; }
    double getMinimalDocSumCoverage() const { return _minimalDocSumCoverage; }

    // API
    //----
    uint32_t CalculateQueueLens_HasLock(uint32_t &dispatchnodes) override;
    bool AreEnginesReady() override;
    virtual void Ping();

    // Downcast
    //---------
    FastS_PlainDataSet * GetPlainDataSet() override { return this; }

    template <class FUN>
    FUN ForEachEngine(FUN fun) {
        for (FastS_EngineBase *ptr : _enginesArray) {
            fun(ptr);
        }
        return fun;
    }

    static bool EngineDocStampOK(time_t haveDocStamp) { return (haveDocStamp != 0); }
};
