// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/fslimits.h>
#include <vespa/searchcore/fdispatch/common/stdincl.h>
#include <vespa/searchcore/config/config-partitions.h>
#include <cassert>

using vespa::config::search::core::PartitionsConfig;

//-----------------------------------------------------------------------

class FastS_EngineDesc
{
private:
    FastS_EngineDesc(const FastS_EngineDesc &);
    FastS_EngineDesc& operator=(const FastS_EngineDesc &);

    FastS_EngineDesc  *_next;
    std::string        _name;
    uint32_t           _confPartID;
    uint32_t           _confRowID;
    uint32_t           _unitrefcost;
    bool               _isBad;
    bool               _confPartIDOverrides;

public:
    explicit FastS_EngineDesc(const char *name)
        : _next(NULL),
          _name(name),
          _confPartID(FastS_NoID32()),
          _confRowID(FastS_NoID32()),
          _unitrefcost(1),
          _isBad(false),
          _confPartIDOverrides(false)
    { }

    void SetNext(FastS_EngineDesc *next) { _next = next; }
    void SetConfPartID(int32_t value) { assert(value >= 0); _confPartID = value; }
    void SetConfPartIDOverrides() { _confPartIDOverrides = true; }
    void SetConfRowID(int32_t value) { assert(value >= 0); _confRowID = value; }
    void SetUnitRefCost(uint32_t value) { _unitrefcost = value; }
    void MarkBad() { _isBad = true; }
    FastS_EngineDesc * GetNext() const { return _next; }
    const char * GetName() const { return _name.c_str(); }
    uint32_t GetConfPartID() const { return _confPartID; }
    bool GetConfPartIDOverrides() const { return _confPartIDOverrides; }
    uint32_t GetConfRowID() const { return _confRowID; }
    uint32_t GetUnitRefCost() const { return _unitrefcost; }
    bool IsBad() const { return _isBad; }
};

//-----------------------------------------------------------------------

class FastS_DataSetDesc
{
private:
    FastS_DataSetDesc(const FastS_DataSetDesc &);
    FastS_DataSetDesc& operator=(const FastS_DataSetDesc &);

    static double _defaultSlowQueryLimitFactor;
    static double _defaultSlowQueryLimitBias;
    static double _defaultSlowDocsumLimitFactor;
    static double _defaultSlowDocsumLimitBias;

public:

    class QueryDistributionMode {
    public:
        enum Mode { 
            RANDOM        = PartitionsConfig::Dataset::RANDOM,
            AUTOMATIC     = PartitionsConfig::Dataset::AUTOMATIC,
            FIXEDROW      = PartitionsConfig::Dataset::FIXEDROW
        };

        QueryDistributionMode(Mode mode, double minGroupCoverage, double latencyDecayRate) :
            _mode(mode),
            _minGroupCoverage(minGroupCoverage),
            _latencyDecayRate(latencyDecayRate),
            _minActivedocsCoverage(0.0)
        { }

        QueryDistributionMode(PartitionsConfig::Dataset::Querydistribution mode, double minGroupCoverage, double latencyDecayRate) :
            QueryDistributionMode(static_cast<Mode>(mode), minGroupCoverage, latencyDecayRate)
        {
        }

        bool operator==(const QueryDistributionMode & rhs) const {
            return _mode == rhs._mode;
        }
        bool operator == (Mode rhs) const {
            return _mode == rhs;
        }
        double getMinGroupCoverage() const { return _minGroupCoverage; }
        double getLatencyDecayRate() const { return _latencyDecayRate; }
        double getMinActivedocsCoverage() const { return _minActivedocsCoverage; }

        void setMinActivedocsCoverage(double val) { _minActivedocsCoverage = val; }
    private:
        Mode    _mode;
        double  _minGroupCoverage;
        double  _latencyDecayRate;
        double  _minActivedocsCoverage;
    };

    static void SetDefaultSlowQueryLimitFactor(double value)
    { _defaultSlowQueryLimitFactor = value; }

    static void SetDefaultSlowQueryLimitBias(double value)
    { _defaultSlowQueryLimitBias = value; }

    static void SetDefaultSlowDocsumLimitFactor(double value)
    { _defaultSlowDocsumLimitFactor = value; }

    static void SetDefaultSlowDocsumLimitBias(double value)
    { _defaultSlowDocsumLimitBias = value; }

private:
    uint32_t _id;
    QueryDistributionMode _queryDistributionMode;

    uint32_t _searchableCopies;
    uint32_t _unitRefCost;           // Cost to reference us
    uint32_t _partBits;              // # bits used to encode part id
    uint32_t _rowBits;               // # bits used to encode row id
    uint32_t _numParts;            // Number of partitions
    uint32_t _firstPart;             // First partition
    uint32_t _minChildParts;       // Minimum partitions live to avoid tempfail
    uint32_t _maxNodesDownPerFixedRow; // max number of nodes down in a row before considering another row.
    bool     _useRoundRobinForFixedRow; // Either plain roundrobin or random.
    uint32_t _maxHitsPerNode;        // max hits requested from single node
    uint32_t _estimateParts;         // number of partitions used for estimate
    uint32_t _estPartCutoff;         // First partition not used for estimate
    bool     _estimatePartsSet;      // has _estimateParts been set ?
    bool     _estPartCutoffSet;      // has _estimatePartsCutoff been set ?
    uint32_t _minOurActive;          // below ==> activate, skip estimates
    uint32_t _maxOurActive;          // above ==> queue
    uint32_t _cutoffOurActive;       // Above ==> cutoff
    uint32_t _minEstActive;          // est below ==> activate
    uint32_t _maxEstActive;          // est below ==> queue, est above cutoff > 0%
    uint32_t _cutoffEstActive;       // est above ==> cutoff 100%
    double   _queueDrainRate;        // max queue drain per second
    double   _queueMaxDrain;         // max queue drain at once
    double   _slowQueryLimitFactor;
    double   _slowQueryLimitBias;
    double   _slowDocsumLimitFactor;
    double   _slowDocsumLimitBias;
    double   _monitorInterval;
    double   _higherCoverageMaxSearchWait;
    double   _higherCoverageMinSearchWait;
    double   _higherCoverageBaseSearchWait;
    double   _minimalSearchCoverage;
    double   _higherCoverageMaxDocSumWait;
    double   _higherCoverageMinDocSumWait;
    double   _higherCoverageBaseDocSumWait;
    double   _minimalDocSumCoverage;

    uint32_t          _engineCnt;    // number of search engines in dataset
    FastS_EngineDesc *_enginesHead;  // first engine in dataset
    FastS_EngineDesc *_enginesTail;  // last engine in dataset

    uint32_t _mpp;  // Minimum number of engines per partition
public:
    explicit FastS_DataSetDesc(uint32_t datasetid);
    ~FastS_DataSetDesc();

    uint32_t GetID() const { return _id; }
    void SetUnitRefCost(uint32_t value) { _unitRefCost = value; }
    void setSearchableCopies(uint32_t value) { _searchableCopies = value; }

    void SetPartBits(uint32_t value) {
       if (value >= MIN_PARTBITS && value <= MAX_PARTBITS)
           _partBits = value;
    }

    void SetRowBits(uint32_t value) {
        if (value <= MAX_ROWBITS)
            _rowBits = value;
    }

    void SetNumParts(uint32_t value) { _numParts = value; }
    void SetFirstPart(uint32_t value) { _firstPart = value; }
    void SetMinChildParts(uint32_t value) { _minChildParts         = value; }
    void setMaxNodesDownPerFixedRow(uint32_t value) { _maxNodesDownPerFixedRow = value; }
    void useRoundRobinForFixedRow(bool value) { _useRoundRobinForFixedRow = value; }
    void SetMaxHitsPerNode(uint32_t value) { _maxHitsPerNode        = value; }
    void SetEstimateParts(uint32_t value) {
        _estimateParts         = value;
        _estimatePartsSet      = true;
    }

    void SetEstPartCutoff(uint32_t value) {
        _estPartCutoff         = value;
        _estPartCutoffSet      = true;
    }

    void SetMinOurActive(uint32_t value) { _minOurActive = value; }
    void SetMaxOurActive(uint32_t value) { _maxOurActive = value; }
    void SetCutoffOurActive(uint32_t value) { _cutoffOurActive = value; }
    void SetMinEstActive(uint32_t value) { _minEstActive = value; }
    void SetMaxEstActive(uint32_t value) { _maxEstActive = value; }
    void SetCutoffEstActive(uint32_t value) { _cutoffEstActive = value; }
    void SetQueueDrainRate(double value) { _queueDrainRate = value; }
    void SetQueueMaxDrain(double value) { _queueMaxDrain = value; }
    void SetSlowQueryLimitFactor(double value) { _slowQueryLimitFactor = value; }
    void SetSlowQueryLimitBias(double value) { _slowQueryLimitBias = value; }
    void SetSlowDocsumLimitFactor(double value) { _slowDocsumLimitFactor = value; }
    void SetSlowDocsumLimitBias(double value) { _slowDocsumLimitBias = value; }

    void SetQueryDistributionMode(QueryDistributionMode queryDistributionMode) {
        _queryDistributionMode = queryDistributionMode;
    }

    QueryDistributionMode GetQueryDistributionMode() { return _queryDistributionMode; }

    FastS_EngineDesc * AddEngine(const char *name);
    uint32_t GetUnitRefCost() const { return _unitRefCost; }
    uint32_t GetPartBits() const { return _partBits; }

    uint32_t GetRowBits() const { return _rowBits; }
    uint32_t GetNumParts() const { return _numParts; }
    uint32_t GetFirstPart() const { return _firstPart; }
    uint32_t GetMinChildParts() const { return _minChildParts; }
    uint32_t getMaxNodesDownPerFixedRow() const { return _maxNodesDownPerFixedRow; }
    bool useRoundRobinForFixedRow() const { return _useRoundRobinForFixedRow; }
    uint32_t GetMaxHitsPerNode() const { return _maxHitsPerNode; }
    uint32_t GetEstimateParts() const { return _estimateParts; }
    uint32_t GetEstPartCutoff() const { return _estPartCutoff; }
    bool IsEstimatePartsSet() const { return _estimatePartsSet; }
    bool IsEstPartCutoffSet() const { return _estPartCutoffSet; }
    uint32_t getSearchableCopies() const { return _searchableCopies; }
    uint32_t GetMinOurActive() const { return _minOurActive; }
    uint32_t GetMaxOurActive() const { return _maxOurActive; }
    uint32_t GetCutoffOurActive() const { return _cutoffOurActive; }
    uint32_t GetMinEstActive() const { return _minEstActive; }
    uint32_t GetMaxEstActive() const { return _maxEstActive; }
    uint32_t GetCutoffEstActive() const { return _cutoffEstActive; }
    double GetQueueDrainRate() const { return _queueDrainRate; }
    double GetQueueMaxDrain() const { return _queueMaxDrain; }
    double GetSlowQueryLimitFactor() const { return _slowQueryLimitFactor; }
    double GetSlowQueryLimitBias() const { return _slowQueryLimitBias; }
    double GetSlowDocsumLimitFactor() const { return _slowDocsumLimitFactor; }
    double GetSlowDocsumLimitBias() const { return _slowDocsumLimitBias; }
    uint32_t GetEngineCnt() const { return _engineCnt; }
    FastS_EngineDesc * GetEngineList() const { return _enginesHead; }
    void setMPP(uint32_t mpp) { _mpp = mpp; }
    uint32_t getMPP() const { return _mpp; }

    void
    setMonitorInterval(double monitorInterval) { _monitorInterval = monitorInterval; }
    double getMonitorInterval() const { return _monitorInterval; }

    void
    setHigherCoverageMaxSearchWait(double higherCoverageMaxSearchWait) {
        _higherCoverageMaxSearchWait = higherCoverageMaxSearchWait;
    }

    double
    getHigherCoverageMaxSearchWait() const {
        return _higherCoverageMaxSearchWait;
    }

    void
    setHigherCoverageMinSearchWait(double higherCoverageMinSearchWait) {
        _higherCoverageMinSearchWait = higherCoverageMinSearchWait;
    }

    double
    getHigherCoverageMinSearchWait() const {
        return _higherCoverageMinSearchWait;
    }

    void
    setHigherCoverageBaseSearchWait(double higherCoverageBaseSearchWait) {
        _higherCoverageBaseSearchWait = higherCoverageBaseSearchWait;
    }

    double
    getHigherCoverageBaseSearchWait() const {
        return _higherCoverageBaseSearchWait;
    }

    void
    setMinimalSearchCoverage(double minimalSearchCoverage) {
        _minimalSearchCoverage = minimalSearchCoverage;
    }

    double
    getMinimalSearchCoverage() const {
        return _minimalSearchCoverage;
    }

    void
    setHigherCoverageMaxDocSumWait(double higherCoverageMaxDocSumWait) {
        _higherCoverageMaxDocSumWait = higherCoverageMaxDocSumWait;
    }

    double
    getHigherCoverageMaxDocSumWait() const {
        return _higherCoverageMaxDocSumWait;
    }

    void
    setHigherCoverageMinDocSumWait(double higherCoverageMinDocSumWait) {
        _higherCoverageMinDocSumWait = higherCoverageMinDocSumWait;
    }

    double
    getHigherCoverageMinDocSumWait() const {
        return _higherCoverageMinDocSumWait;
    }

    void
    setHigherCoverageBaseDocSumWait(double higherCoverageBaseDocSumWait) {
        _higherCoverageBaseDocSumWait = higherCoverageBaseDocSumWait;
    }

    double
    getHigherCoverageBaseDocSumWait() const {
        return _higherCoverageBaseDocSumWait;
    }

    void
    setMinimalDocSumCoverage(double minimalDocSumCoverage) {
        _minimalDocSumCoverage = minimalDocSumCoverage;
    }

    double
    getMinimalDocSumCoverage() const {
        return _minimalDocSumCoverage;
    }

    void FinalizeConfig();
};

//-----------------------------------------------------------------------

class FastS_DataSetCollDesc
{
private:
    FastS_DataSetCollDesc(const FastS_DataSetCollDesc &);
    FastS_DataSetCollDesc& operator=(const FastS_DataSetCollDesc &);

    FastS_DataSetDesc **_datasets;
    uint32_t            _datasets_size;

    bool                _frozen;
    bool                _error;

    void HandleDeprecatedFPEstPartsOption();
    bool CheckIntegrity();

public:
    FastS_DataSetCollDesc();
    ~FastS_DataSetCollDesc();

    FastS_DataSetDesc *LookupCreateDataSet(uint32_t datasetid);

    bool Freeze();

    uint32_t GetMaxNumDataSets() const { return _datasets_size; }

    FastS_DataSetDesc *GetDataSet(uint32_t datasetid) const {
        return (datasetid < _datasets_size)
            ? _datasets[datasetid]
            : NULL;
    }

    bool ReadConfig(const PartitionsConfig& partmap);
};

//-----------------------------------------------------------------------

