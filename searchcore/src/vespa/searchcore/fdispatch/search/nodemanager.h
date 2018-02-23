// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "child_info.h"
#include "configdesc.h"
#include <vespa/config/helper/configfetcher.h>
#include <vespa/searchcore/fdispatch/common/queryperf.h>
#include <vespa/vespalib/net/simple_component_config_producer.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/vespalib/util/executor.h>
#include <mutex>

using vespa::config::search::core::PartitionsConfig;

class FastS_DataSetBase;
class FastS_AppContext;
class FastS_DataSetCollection;
class FastS_TimeKeeper;

class FastS_NodeManager : public config::IFetcherCallback<PartitionsConfig>
{
private:
    FastS_NodeManager(const FastS_NodeManager &);
    FastS_NodeManager& operator=(const FastS_NodeManager &);

    vespalib::SimpleComponentConfigProducer &_componentConfig;

    std::mutex        _managerLock;
    std::mutex        _configLock;
    FastS_AppContext *_appCtx;
    uint32_t          _mldPartit;
    uint32_t          _mldDocStamp; // Bumped for all cache flushes
    uint32_t          _mldDocStampMin;  // Bumped for global cache flush
    uint32_t          _gencnt;

    FastS_QueryPerf   _queryPerf;


    std::unique_ptr<config::ConfigFetcher> _fetcher;
    config::ConfigUri _configUri;

    char                    *_lastPartMap;
    FastS_DataSetCollection *_datasetCollection; // current node config
    FastS_DataSetCollection *_oldDSCList;  // list of old node configs

    bool              _tempFail;
    bool              _failed;
    bool              _hasDsc;

    volatile bool     _checkTempFailScheduled;
    volatile bool     _shutdown;

protected:
    void SetFailed() { _failed = true; }

    void configure(std::unique_ptr<PartitionsConfig> cfg) override;

public:
    FastS_NodeManager(vespalib::SimpleComponentConfigProducer &componentConfig,
                      FastS_AppContext *appCtx,
                      uint32_t partition);
    ~FastS_NodeManager();

    void SubscribePartMap(const config::ConfigUri & configUri);

    uint32_t GetMldPartition() const { return _mldPartit; }
    uint32_t GetMldDocstamp();
    uint32_t GetGenCnt() const { return _gencnt; }

    bool Failed() const { return _failed; }
    bool GetTempFail() const { return _tempFail; }

    void ScheduleCheckTempFail(uint32_t datasetid) {
        (void) datasetid;
        _checkTempFailScheduled = true;
    }

    FastS_AppContext *GetAppContext() { return _appCtx; }
    FastS_DataSetCollection *PeekDataSetCollection()
    { return _datasetCollection; }

    void CheckTempFail();
    uint32_t SetPartMap(const PartitionsConfig& partmap, unsigned int waitms);
    uint32_t SetCollDesc(FastS_DataSetCollDesc *configDesc, unsigned int waitms);
    uint32_t SetDataSetCollection(FastS_DataSetCollection *dsc);
    FastS_DataSetCollection *GetDataSetCollection();
    uint32_t GetTotalPartitions();
    ChildInfo getChildInfo();
    void ShutdownConfig();

    /**
     * log query performance. This method should only be invoked from
     * the FNET thread.
     **/
    void logPerformance(vespalib::Executor &executor);

    void CheckEvents(FastS_TimeKeeper *timeKeeper); // invoked by FNET thread
};

