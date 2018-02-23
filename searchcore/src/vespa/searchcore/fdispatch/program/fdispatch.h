// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/fnet.h>
#include <vespa/searchcore/fdispatch/common/appcontext.h>
#include <vespa/searchlib/engine/transportserver.h>
#include <vespa/searchcore/config/config-fdispatchrc.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/config/helper/configfetcher.h>
#include <vespa/vespalib/net/simple_component_config_producer.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

class FastS_NodeManager;
class FastS_fdispatch_RPC;

namespace fdispatch {

class EngineAdapter;

class FastS_FNETAdapter
{
private:
    FastS_AppContext  *_appCtx;
    FastS_NodeManager *_nodeManager;
    FastS_TimeKeeper  *_timeKeeper;
    FNET_Transport    *_transport;
    double             _last_now;     // latency check
    uint32_t           _live_counter; // latency check

    struct MyTask : FNET_Task {
        FastS_FNETAdapter &self;
        MyTask(FNET_Scheduler *scheduler, FastS_FNETAdapter &self_in)
            : FNET_Task(scheduler), self(self_in) {}
        virtual void PerformTask() override {
            self.perform();
            ScheduleNow();
        }
    };
    std::unique_ptr<MyTask> _task;

public:
    FastS_FNETAdapter(FastS_AppContext *appCtx);
    ~FastS_FNETAdapter();
    void init();
    void perform();
    uint32_t GetLiveCounter() const { return _live_counter; }
    void fini();
};


/**
 * Note: There is only one instance of this.
 */
class Fdispatch : public FastS_AppContext,
                  public config::IFetcherCallback<vespa::config::search::core::FdispatchrcConfig>
{
private:
    typedef search::engine::TransportServer TransportServer;
    typedef vespa::config::search::core::FdispatchrcConfig FdispatchrcConfig;
    Fdispatch(const Fdispatch &);
    Fdispatch& operator=(const Fdispatch &);

    vespalib::ThreadStackExecutor           _executor;
    std::unique_ptr<FastOS_ThreadPool>      _mypool;
    std::unique_ptr<EngineAdapter>          _engineAdapter;
    std::unique_ptr<TransportServer>        _transportServer;
    vespalib::SimpleComponentConfigProducer _componentConfig;
    std::unique_ptr<FastS_NodeManager>      _nodeManager;
    std::unique_ptr<FNET_Transport>         _transport;
    FastS_FNETAdapter                       _FNET_adapter;
    std::unique_ptr<FastS_fdispatch_RPC>    _rpc;
    std::unique_ptr<FdispatchrcConfig>      _config;
    config::ConfigUri                       _configUri;
    config::ConfigFetcher                   _fdispatchrcFetcher;
    vespalib::RandomGen                     _rndGen;
    unsigned int _partition;
    bool         _tempFail;
    bool         _FNETLiveCounterDanger;
    bool         _FNETLiveCounterWarned;
    bool         _FNETLiveCounterFailed;
    bool         _transportStarted;
    unsigned int _lastFNETLiveCounter;
    FastOS_Time  _FNETLiveCounterDangerStart;
    unsigned int _timeouts;
    unsigned int _checkLimit;
    int          _healthPort;
    std::atomic<bool> _needRestart;
    void configure(std::unique_ptr<FdispatchrcConfig> cfg) override;
public:
    // Implements FastS_AppContext
    virtual FNET_Transport *GetFNETTransport() override;
    virtual FNET_Scheduler *GetFNETScheduler() override;
    virtual FastS_NodeManager *GetNodeManager() override;
    virtual FastS_DataSetCollection *GetDataSetCollection() override;
    virtual FastOS_ThreadPool *GetThreadPool() override;
    virtual void logPerformance() override;
    virtual uint32_t getDispatchLevel() override;
    bool CheckTempFail();
    bool Failed();
    bool Init();
    int getHealthPort() const { return _healthPort; }
    vespalib::SimpleComponentConfigProducer &getComponentConfig() { return _componentConfig; }

    Fdispatch(const config::ConfigUri &configUri);
    ~Fdispatch();
};

}

