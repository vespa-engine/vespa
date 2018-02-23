// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "nodemanager.h"
#include "datasetcollection.h"
#include "plain_dataset.h"
#include "engine_base.h"
#include <vespa/config/common/exceptions.h>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".search.nodemanager");

void
FastS_NodeManager::configure(std::unique_ptr<PartitionsConfig> cfg)
{
    LOG(config, "configuring datasetcollection from '%s'",
        _configUri.getConfigId().c_str());
    SetPartMap(*cfg, 2000);
    _componentConfig.addConfig(
            vespalib::ComponentConfigProducer::Config("fdispatch.nodemanager",
                                                      _fetcher->getGeneration(),
                                                      "will not update generation unless config has changed"));
}


class AdminBadEngines
{
    std::set<vespalib::string> _bad;
public:
    void addAdminBad(const vespalib::string &name) {
        _bad.insert(name);
    }

    bool isAdminBad(const vespalib::string &name) const {
        return _bad.find(name) != _bad.end();
    }
};

class CollectAdminBadEngines
{
    AdminBadEngines &_adminBadEngines;

public:

    CollectAdminBadEngines(AdminBadEngines &adminBadEngines)
        : _adminBadEngines(adminBadEngines)
    {
    }
    
    void operator()(FastS_EngineBase* engine)
    {
        if (engine->isAdminBad()) {
            _adminBadEngines.addAdminBad(engine->GetName());
        }
    }
};


class PropagateAdminBadEngines
{
    const AdminBadEngines &_adminBadEngines;

public:

    PropagateAdminBadEngines(const AdminBadEngines &adminBadEngines)
        : _adminBadEngines(adminBadEngines)
    {
    }
    
    void operator()(FastS_EngineBase* engine)
    {
        if (_adminBadEngines.isAdminBad(engine->GetName())) {
            engine->MarkBad(FastS_EngineBase::BAD_ADMIN);
        }
    }
};


FastS_NodeManager::FastS_NodeManager(vespalib::SimpleComponentConfigProducer &componentConfig,
                                     FastS_AppContext *appCtx,
                                     uint32_t partition)
    : _componentConfig(componentConfig),
      _managerLock(),
      _configLock(),
      _appCtx(appCtx),
      _mldPartit(partition),
      _mldDocStamp(0),
      _mldDocStampMin(0),
      _gencnt(0),
      _queryPerf(),
      _fetcher(),
      _configUri(config::ConfigUri::createEmpty()),
      _lastPartMap(NULL),
      _datasetCollection(NULL),
      _oldDSCList(NULL),
      _tempFail(false),
      _failed(false),
      _hasDsc(false),
      _checkTempFailScheduled(false),
      _shutdown(false)
{
    _datasetCollection = new FastS_DataSetCollection(_appCtx);
    FastS_assert(_datasetCollection != NULL);
    _datasetCollection->Configure(NULL, 0);
    FastOS_Time now;
    now.SetNow();
    _mldDocStamp = now.GetSeconds();
    _mldDocStampMin = _mldDocStamp;
}


FastS_NodeManager::~FastS_NodeManager()
{
    free(_lastPartMap);
    FastS_assert(_datasetCollection != NULL);
    _datasetCollection->subRef();
}

void
FastS_NodeManager::CheckTempFail()
{
    bool tempfail;

    _checkTempFailScheduled = false;
    tempfail = false;
    {
        std::lock_guard<std::mutex> mangerGuard(_managerLock);
        FastS_DataSetCollection *dsc = PeekDataSetCollection();
        for (unsigned int i = 0; i < dsc->GetMaxNumDataSets(); i++) {
            FastS_DataSetBase *ds;
            FastS_PlainDataSet *ds_plain;
            if ((ds = dsc->PeekDataSet(i)) != NULL &&
                (ds_plain = ds->GetPlainDataSet()) != NULL &&
                ds_plain->GetTempFail()) {
                tempfail = true;
                break;
            }
        }
    }
    _tempFail = tempfail;
}

void
FastS_NodeManager::SubscribePartMap(const config::ConfigUri & configUri)
{
    vespalib::string configId(configUri.getConfigId());
    LOG(debug, "loading new datasetcollection from %s", configId.c_str());
    try {
        _configUri = configUri;
        _fetcher.reset(new config::ConfigFetcher(_configUri.getContext()));
        _fetcher->subscribe<PartitionsConfig>(configId, this);
        _fetcher->start();
        if (_gencnt == 0) {
            throw new config::InvalidConfigException("failure during initial configuration: bad partition map");
        }
    } catch (std::exception &ex) {
        LOG(error, "Runtime exception: %s", (const char *) ex.what());
        EV_STOPPING("", "bad partitions config");
        exit(1);
    }
}


uint32_t
FastS_NodeManager::SetPartMap(const PartitionsConfig& partmap,
                              unsigned int waitms)
{
    std::lock_guard<std::mutex> configGuard(_configLock);
    FastS_DataSetCollDesc *configDesc = new FastS_DataSetCollDesc();
    if (!configDesc->ReadConfig(partmap)) {
        LOG(error, "NodeManager::SetPartMap: Failed to load configuration");
        delete configDesc;
        return 0;
    }
    int retval = SetCollDesc(configDesc, waitms);
    return retval;
}


uint32_t
FastS_NodeManager::SetCollDesc(FastS_DataSetCollDesc *configDesc,
                               unsigned int waitms)
{
    FastS_DataSetCollection *newCollection;
    uint32_t gencnt;

    if (_shutdown) return 0;

    AdminBadEngines adminBad;

    {
        CollectAdminBadEngines adminBadCollect(adminBad);
        FastS_DataSetCollection *dsc = GetDataSetCollection();
        for (uint32_t i = 0; i < dsc->GetMaxNumDataSets(); i++) {
            FastS_DataSetBase *ds;
            FastS_PlainDataSet *ds_plain;
            if ((ds = dsc->PeekDataSet(i)) == NULL ||
                (ds_plain = ds->GetPlainDataSet()) == NULL)
                continue;
            
            ds_plain->ForEachEngine(adminBadCollect);
        }
        dsc->subRef();
    }


    newCollection = new FastS_DataSetCollection(_appCtx);
    if (!newCollection->Configure(configDesc, _gencnt + 1)) {
        LOG(error, "NodeManager::SetPartMap: Inconsistent configuration");
        newCollection->subRef();
        return 0;
    }

    {
        PropagateAdminBadEngines adminBadPropagate(adminBad);
        for (uint32_t i = 0; i < newCollection->GetMaxNumDataSets(); i++) {
            FastS_DataSetBase *ds;
            FastS_PlainDataSet *ds_plain;
            if ((ds = newCollection->PeekDataSet(i)) == NULL ||
                (ds_plain = ds->GetPlainDataSet()) == NULL)
                continue;
            
            ds_plain->ForEachEngine(adminBadPropagate);
        }
    }

    if (waitms > 0) {
        FastOS_Time last;
        unsigned int rwait;
        bool allup;
        last.SetNow();
        while (1) {
            allup = newCollection->AreEnginesReady();
            rwait = (unsigned int) last.MilliSecsToNow();
            if (rwait >= waitms || allup)
                break;
            FastOS_Thread::Sleep(100);
        };
        if (allup) {
            LOG(debug, "All new engines up after %d ms", rwait);
        } else {
            LOG(debug, "Some new engines still down after %d ms", rwait);
        }
    }

    gencnt = SetDataSetCollection(newCollection);

    ScheduleCheckTempFail(FastS_NoID32());
    return gencnt;
}



/**
 * When calling this method, a single reference on the 'dsc' parameter
 * is passed to the monitor object.
 *
 * @return generation count, or 0 on fail.
 * @param dsc new dataset collection. A single reference is passed
 *            to the monitor when this method is invoked.
 **/
uint32_t
FastS_NodeManager::SetDataSetCollection(FastS_DataSetCollection *dsc)
{
    if (dsc == NULL)
        return 0;

    uint32_t                 gencnt  = 0;
    FastS_DataSetCollection *old_dsc = NULL;

    if (!dsc->IsValid()) {
        LOG(error, "NodeManager::SetDataSetCollection: Inconsistent configuration");
        dsc->subRef();

    } else {
        {
            std::lock_guard<std::mutex> managerGuard(_managerLock);
            _gencnt++;
            gencnt = _gencnt;

            old_dsc = _datasetCollection;
            _datasetCollection = dsc;

            // put old config on service list
            FastS_assert(old_dsc != NULL);
            if (!old_dsc->IsLastRef()) {
                old_dsc->_nextOld = _oldDSCList;
                _oldDSCList = old_dsc;
                old_dsc = NULL;
            }
            _hasDsc = true;
        }

        if (old_dsc != NULL)
            old_dsc->subRef();
    }
    return gencnt;
}


FastS_DataSetCollection *
FastS_NodeManager::GetDataSetCollection()
{
    FastS_DataSetCollection *ret;

    std::lock_guard<std::mutex> managerGuard(_managerLock);
    ret = _datasetCollection;
    FastS_assert(ret != NULL);
    ret->addRef();

    return ret;
}


void
FastS_NodeManager::ShutdownConfig()
{
    FastS_DataSetCollection *dsc;
    FastS_DataSetCollection *old_dsc;

    {
        std::lock_guard<std::mutex> configGuard(_configLock);
        std::lock_guard<std::mutex> managerGuard(_managerLock);
        _shutdown = true;           // disallow SetPartMap
        dsc = _datasetCollection;
        _datasetCollection = new FastS_DataSetCollection(_appCtx);
        _datasetCollection->Configure(NULL, 0);
        old_dsc = _oldDSCList;
        _oldDSCList = NULL;
    }
    dsc->AbortQueryQueues();
    dsc->subRef();
    while (old_dsc != NULL) {
        dsc = old_dsc;
        old_dsc = old_dsc->_nextOld;
        dsc->_nextOld = NULL;
        dsc->AbortQueryQueues();
        dsc->subRef();
    }
}


uint32_t
FastS_NodeManager::GetTotalPartitions()
{
    uint32_t ret;

    ret = 0;
    std::lock_guard<std::mutex> managerGuard(_managerLock);
    FastS_DataSetCollection *dsc = PeekDataSetCollection();
    for (unsigned int i = 0; i < dsc->GetMaxNumDataSets(); i++) {
        FastS_DataSetBase *ds;
        FastS_PlainDataSet *ds_plain;
        if ((ds = dsc->PeekDataSet(i)) != NULL &&
            (ds_plain = ds->GetPlainDataSet()) != NULL)
            ret += ds_plain->GetPartitions();
    }
    return ret;
}


ChildInfo
FastS_NodeManager::getChildInfo()
{
    ChildInfo r;
    r.activeDocs.valid = true;
    FastS_DataSetCollection *dsc = GetDataSetCollection();

    for (unsigned int i = 0; i < dsc->GetMaxNumDataSets(); i++) {
        FastS_DataSetBase *ds;
        FastS_PlainDataSet *ds_plain;
        if ((ds = dsc->PeekDataSet(i)) == NULL ||
            (ds_plain = ds->GetPlainDataSet()) == NULL)
            continue;
        r.maxNodes    += ds_plain->_partMap._childmaxnodesSinceReload;
        r.activeNodes += ds_plain->_partMap._childnodes;
        r.maxParts    += ds_plain->_partMap._childmaxpartsSinceReload;
        r.activeParts += ds_plain->_partMap._childparts;
        PossCount rowActive = ds_plain->getActiveDocs();
        if (rowActive.valid) {
            r.activeDocs.count += rowActive.count;
        } else {
            r.activeDocs.valid = false;
        }
    }

    dsc->subRef();
    return r;
}


void
FastS_NodeManager::logPerformance(vespalib::Executor &executor)
{
    _queryPerf.reset();
    FastS_DataSetCollection *dsc = GetDataSetCollection();

    for (unsigned int i = 0; i < dsc->GetMaxNumDataSets(); i++) {
        if (dsc->PeekDataSet(i) != NULL) {
            dsc->PeekDataSet(i)->addPerformance(_queryPerf);
        }
    }

    dsc->subRef();
    executor.execute(_queryPerf.make_log_task());
}


void
FastS_NodeManager::CheckEvents(FastS_TimeKeeper *timeKeeper)
{
    // CHECK SCHEDULED OPERATIONS

    if (_checkTempFailScheduled)
        CheckTempFail();

    // CHECK QUERY QUEUES

    FastS_DataSetCollection *dsc = GetDataSetCollection();

    dsc->CheckQueryQueues(timeKeeper);
    dsc->subRef();

    // check old query queues and discard old configs

    FastS_DataSetCollection *old_dsc;
    FastS_DataSetCollection *prev = NULL;
    FastS_DataSetCollection *tmp;

    {
        std::lock_guard<std::mutex> managerGuard(_managerLock);
        old_dsc = _oldDSCList;
    }

    while (old_dsc != NULL) {
        if (old_dsc->IsLastRef()) {
            if (prev == NULL) {
                std::unique_lock<std::mutex> managerGuard(_managerLock);
                if (_oldDSCList == old_dsc) {
                    _oldDSCList = old_dsc->_nextOld;
                } else {
                    prev = _oldDSCList;
                    managerGuard.unlock();
                    while (prev->_nextOld != old_dsc)
                        prev = prev->_nextOld;

                    prev->_nextOld = old_dsc->_nextOld;
                }
            } else {
                prev->_nextOld = old_dsc->_nextOld;
            }
            tmp = old_dsc;
            old_dsc = old_dsc->_nextOld;
            tmp->subRef();

        } else {

            old_dsc->CheckQueryQueues(timeKeeper);
            prev = old_dsc;
            old_dsc = old_dsc->_nextOld;
        }
    }
}

uint32_t
FastS_NodeManager::GetMldDocstamp()
{
    if (!_hasDsc)
        return 0;
    return _mldDocStamp;
}
