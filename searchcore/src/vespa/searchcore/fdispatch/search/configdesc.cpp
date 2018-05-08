// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configdesc.h"
#include <vespa/searchcore/util/log.h>

#include <vespa/log/log.h>
LOG_SETUP(".search.configdesc");

//----------------------------------------------------------------------

double FastS_DataSetDesc::_defaultSlowQueryLimitFactor  = 0.0;
double FastS_DataSetDesc::_defaultSlowQueryLimitBias    = 100.0;
double FastS_DataSetDesc::_defaultSlowDocsumLimitFactor = 0.0;
double FastS_DataSetDesc::_defaultSlowDocsumLimitBias   = 100.0;


FastS_DataSetDesc::FastS_DataSetDesc(uint32_t datasetid)
    : _id(datasetid),
      _queryDistributionMode(QueryDistributionMode::AUTOMATIC, 100.0, 10000),
      _searchableCopies(1),
      _unitRefCost(0),
      _partBits(6),
      _rowBits(0),
      _numParts(0),
      _firstPart(0),
      _minChildParts(0),
      _maxNodesDownPerFixedRow(0),
      _useRoundRobinForFixedRow(true),
      _maxHitsPerNode(static_cast<uint32_t>(-1)),
      _estimateParts(1),
      _estPartCutoff(1),
      _estimatePartsSet(false),
      _estPartCutoffSet(false),
      _minOurActive(500),
      _maxOurActive(500),
      _cutoffOurActive(1000),
      _minEstActive(500),
      _maxEstActive(1000),
      _cutoffEstActive(1000),
      _queueDrainRate(400.0),
      _queueMaxDrain(40.0),
      _slowQueryLimitFactor(_defaultSlowQueryLimitFactor),
      _slowQueryLimitBias(_defaultSlowQueryLimitBias),
      _slowDocsumLimitFactor(_defaultSlowDocsumLimitFactor),
      _slowDocsumLimitBias(_defaultSlowDocsumLimitBias),
      _monitorInterval(1.0),
      _higherCoverageMaxSearchWait(1.0),
      _higherCoverageMinSearchWait(0.0),
      _higherCoverageBaseSearchWait(0.1),
      _minimalSearchCoverage(100.0),
      _higherCoverageMaxDocSumWait(0.3),
      _higherCoverageMinDocSumWait(0.1),
      _higherCoverageBaseDocSumWait(0.1),
      _minimalDocSumCoverage(100.0),
      _engineCnt(0),
      _enginesHead(NULL),
      _enginesTail(NULL),
      _mpp(1)
{
}


FastS_DataSetDesc::~FastS_DataSetDesc()
{
    while (_enginesHead != NULL) {
        FastS_EngineDesc *engine = _enginesHead;
        _enginesHead = engine->GetNext();
        delete engine;
    }
}


FastS_EngineDesc *
FastS_DataSetDesc::AddEngine(const char *name)
{
    FastS_EngineDesc *engine = new FastS_EngineDesc(name);
    FastS_assert(engine != NULL);

    engine->SetNext(NULL);
    if (_enginesHead == NULL)
        _enginesHead = engine;
    else
        _enginesTail->SetNext(engine);
    _enginesTail = engine;
    _engineCnt++;
    return engine;
}


void
FastS_DataSetDesc::FinalizeConfig()
{
    /* assume 1 partition if number of partitions was not specified */
    if (GetNumParts() == 0) {
        LOG(warning,
            "Setting partitions to 1 in dataset %u",
            (unsigned int) GetID());
        SetNumParts(1);
    }

    if (!_estPartCutoffSet ||
        _estPartCutoff > _numParts ||
        _estPartCutoff == 0)
        _estPartCutoff = _numParts;
}

//----------------------------------------------------------------------

bool
FastS_DataSetCollDesc::CheckIntegrity()
{
    bool rc = true;

    for (uint32_t i = 0; i < _datasets_size; i++) {
        FastS_DataSetDesc *d = _datasets[i];
        if (d != NULL) {
            if (d->GetEngineCnt() == 0) {
                LOG(warning, "plain dataset %d has no engines", d->GetID());
            }

            if (d->GetNumParts() == 0) {
                LOG(warning, "plain dataset %d has no partitions", d->GetID());
            }

            // check engine configuration
            {
                uint32_t partBits = d->GetPartBits();
                uint32_t rowBits  = d->GetRowBits();
                uint32_t minPart  = d->GetFirstPart();
                uint32_t maxPart  = minPart + (1 << partBits) - 2;
                uint32_t maxRow   = (rowBits > 0)? (1 << rowBits) - 1 : 0;
                uint32_t enginePartCnt = 0;
                FastS_assert(partBits > 0);
                bool *partidUsed = new bool[maxPart];
                for (uint32_t j = 0; j < maxPart; j++)
                    partidUsed[j] = false;

                for (FastS_EngineDesc *engine = d->GetEngineList();
                     engine != NULL; engine = engine->GetNext()) {

                    bool     bad    = false;
                    uint32_t partid = engine->GetConfPartID();
                    uint32_t rowid  = engine->GetConfRowID();

                    if (partid != FastS_NoID32() &&
                        (partid < minPart || partid > maxPart))
                    {
                        LOG(error, "engine '%s' in dataset %d has partid %d, legal range is [%d,%d] (partbits = %d)",
                            engine->GetName(), d->GetID(), partid,
                            minPart, maxPart, partBits);
                        bad = true;
                    }

                    if (rowid && rowid != FastS_NoID32()) {
                        if (rowBits == 0) {
                            LOG(warning, "rowid (%d) on engine '%s' in dataset %d "
                                "will be ignored because rowbits is 0",
                                rowid, engine->GetName(), d->GetID());
                        } else if (rowid > maxRow) {
                            LOG(error, "engine '%s' in dataset %d has rowid %d, legal range is [%d,%d] (rowbits = %d)",
                                engine->GetName(), d->GetID(), rowid,
                                0, maxRow, rowBits);
                            bad = true;
                        }
                    }
                    if (bad) {
                        LOG(error, "marking engine '%s' in dataset %d as BAD due to illegal configuration",
                            engine->GetName(), d->GetID());
                        engine->MarkBad();
                    }

                    if (partid != FastS_NoID32() &&
                        (partid >= minPart || partid <= maxPart)) {
                        if (!partidUsed[partid]) {
                            enginePartCnt++;
                            partidUsed[partid] = true;
                        }
                    } else {
                        enginePartCnt++;
                    }
                }
                delete [] partidUsed;
                if (d->GetNumParts() < enginePartCnt) {
                    LOG(warning,
                        "plain dataset %d has "
                        "%d engines with different partids, "
                        "but only %d partitions",
                        d->GetID(),
                        enginePartCnt,
                        d->GetNumParts());
                }
            }
        }
    }

    return rc;
}



FastS_DataSetCollDesc::FastS_DataSetCollDesc()
    : _datasets(NULL),
      _datasets_size(0),
      _frozen(false),
      _error(false)
{
}


FastS_DataSetCollDesc::~FastS_DataSetCollDesc()
{
    if (_datasets != NULL) {
        for (uint32_t i = 0; i < _datasets_size; i++) {
            if (_datasets[i] != NULL) {
                delete _datasets[i];
            }
        }
        delete [] _datasets;
    }
}


FastS_DataSetDesc *
FastS_DataSetCollDesc::LookupCreateDataSet(uint32_t datasetid)
{
    FastS_assert(!_frozen);

    if (datasetid >= _datasets_size) {
        uint32_t newSize = datasetid + 1;

        FastS_DataSetDesc **newArray = new FastS_DataSetDesc*[newSize];
        FastS_assert(newArray != NULL);

        uint32_t i;
        for (i = 0; i < _datasets_size; i++)
            newArray[i] = _datasets[i];

        for (; i < newSize; i++)
            newArray[i] = NULL;

        delete [] _datasets;
        _datasets = newArray;
        _datasets_size = newSize;
    }

    if (_datasets[datasetid] == NULL) {
        _datasets[datasetid] = new FastS_DataSetDesc(datasetid);
    }

    return _datasets[datasetid];
}


bool
FastS_DataSetCollDesc::Freeze()
{
    if (!_frozen) {
        _frozen = true;

        for (uint32_t i = 0; i < _datasets_size; i++)
            if (_datasets[i] != NULL)
                _datasets[i]->FinalizeConfig();

        _error = !CheckIntegrity();
    }
    return !_error;
}

//----------------------------------------------------------------------
bool
FastS_DataSetCollDesc::ReadConfig(const PartitionsConfig& partmap)
{
    FastS_assert(!_frozen);

    int datasetcnt = partmap.dataset.size();

    if (datasetcnt < 1) {
        LOG(error, "no datasets in partitions config");
        return false;
    }
    for (int i=0; i < datasetcnt; i++) {
        typedef PartitionsConfig::Dataset Dsconfig;
        const Dsconfig dsconfig = partmap.dataset[i];

        FastS_DataSetDesc *dataset = LookupCreateDataSet(dsconfig.id);

        dataset->setSearchableCopies(dsconfig.searchablecopies);
        dataset->SetUnitRefCost(dsconfig.refcost);
        dataset->SetPartBits(dsconfig.partbits);
        dataset->SetRowBits(dsconfig.rowbits);
        dataset->SetNumParts(dsconfig.numparts);
        dataset->SetMinChildParts(dsconfig.minpartitions);
        dataset->setMaxNodesDownPerFixedRow(dsconfig.maxnodesdownperfixedrow);
        dataset->useRoundRobinForFixedRow(dsconfig.useroundrobinforfixedrow);
        dataset->SetMaxHitsPerNode(dsconfig.maxhitspernode);
        dataset->SetFirstPart(dsconfig.firstpart);
        dataset->SetMinOurActive(dsconfig.minactive);
        dataset->SetMaxOurActive(dsconfig.maxactive);
        dataset->SetCutoffOurActive(dsconfig.cutoffactive);
        dataset->SetMinEstActive(dsconfig.minestactive);
        dataset->SetMaxEstActive(dsconfig.maxestactive);
        dataset->SetCutoffEstActive(dsconfig.cutoffestactive);
        dataset->SetQueueDrainRate(dsconfig.queuedrainrate);
        dataset->SetQueueMaxDrain(dsconfig.queuedrainmax);
        dataset->SetSlowQueryLimitFactor(dsconfig.slowquerylimitfactor);
        dataset->SetSlowQueryLimitBias(dsconfig.slowquerylimitbias);
        dataset->SetSlowDocsumLimitFactor(dsconfig.slowdocsumlimitfactor);
        dataset->SetSlowDocsumLimitBias(dsconfig.slowdocsumlimitbias);
        dataset->setMonitorInterval(dsconfig.monitorinterval);
        dataset->setHigherCoverageMaxSearchWait(dsconfig.higherCoverageMaxsearchwait);
        dataset->setHigherCoverageMinSearchWait(dsconfig.higherCoverageMinsearchwait);
        dataset->setHigherCoverageBaseSearchWait(dsconfig.higherCoverageBasesearchwait);
        dataset->setMinimalSearchCoverage(dsconfig.minimalSearchcoverage);
        dataset->setHigherCoverageMaxDocSumWait(dsconfig.higherCoverageMaxdocsumwait);
        dataset->setHigherCoverageMinDocSumWait(dsconfig.higherCoverageMindocsumwait);
        dataset->setHigherCoverageBaseDocSumWait(dsconfig.higherCoverageBasedocsumwait);
        dataset->setMinimalDocSumCoverage(dsconfig.minimalDocsumcoverage);
        FastS_DataSetDesc::QueryDistributionMode distMode(dsconfig.querydistribution,
                                                          dsconfig.minGroupCoverage,
                                                          dsconfig.latencyDecayRate);
        distMode.setMinActivedocsCoverage(dsconfig.minActivedocsCoverage);
        dataset->SetQueryDistributionMode(distMode);
        dataset->setMPP(dsconfig.mpp);
        if (dsconfig.estparts > 0)
            dataset->SetEstimateParts(dsconfig.estparts);
        if (dsconfig.estpartcutoff > 0)
            dataset->SetEstPartCutoff(dsconfig.estpartcutoff);

        int enginecnt = dsconfig.engine.size();

        for (int j=0; j < enginecnt; j++) {
            const Dsconfig::Engine& engconfig = dsconfig.engine[j];

            FastS_EngineDesc *engine = dataset->AddEngine(engconfig.nameAndPort.c_str());

            engine->SetUnitRefCost(engconfig.refcost);
            engine->SetConfRowID(engconfig.rowid);
            engine->SetConfPartID(engconfig.partid);
            if (engconfig.overridepartids)
                engine->SetConfPartIDOverrides();
        }
    }
    return true;
}
