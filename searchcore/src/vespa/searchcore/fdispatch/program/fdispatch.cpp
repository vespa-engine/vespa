// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fdispatch.h" 
#include "engineadapter.h"
#include "rpc.h"
#include <vespa/searchcore/fdispatch/search/querycacheutil.h>
#include <vespa/searchcore/fdispatch/search/nodemanager.h>
#include <vespa/searchcore/util/eventloop.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/config/helper/configgetter.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".fdispatch");

#ifndef V_TAG
#define V_TAG "NOTAG"
#endif

using search::fs4transport::FS4PersistentPacketStreamer;
using vespa::config::search::core::FdispatchrcConfig;
using vespa::config::search::core::internal::InternalFdispatchrcType;
using vespalib::compression::CompressionConfig;

char FastS_VersionTag[] = V_TAG;

namespace fdispatch
{

FastS_FNETAdapter::FastS_FNETAdapter(FastS_AppContext *appCtx)
    : _appCtx(appCtx),
      _nodeManager(),
      _timeKeeper(NULL),
      _transport(NULL),
      _last_now(0.0),
      _live_counter(0),
      _task()
{ }

FastS_FNETAdapter::~FastS_FNETAdapter()
{
    fini();
}

void
FastS_FNETAdapter::init()
{
    _nodeManager = _appCtx->GetNodeManager();
    _timeKeeper  = _appCtx->GetTimeKeeper();
    _transport   = _appCtx->GetFNETTransport();
    _last_now    = _timeKeeper->GetTime();
    _task.reset(new MyTask(_transport->GetScheduler(), *this));
    _task->ScheduleNow();
}

void
FastS_FNETAdapter::perform()
{
    double now = _timeKeeper->GetTime();
    double delta = now - _last_now;
    if (delta >= 3.0) {
        LOG(warning, "FNET loop high latency: %.3f", delta);
    }
    _last_now = now;
    ++_live_counter;
    _nodeManager->CheckEvents(_timeKeeper);
}

void
FastS_FNETAdapter::fini()
{
    if (_task) {
        _task->Kill();
        _task.reset();
    }
}

Fdispatch::~Fdispatch()
{
    if (_transportServer) {
        _transportServer->shutDown(); // sync shutdown
    }
    _FNET_adapter.fini();
    if (_nodeManager) {
        _nodeManager->ShutdownConfig();
    }
    if (_transport && _transportStarted) {
        _transport->ShutDown(true); // sync shutdown
    }
    if (_rpc) {
        _rpc->ShutDown(); // sync shutdown
    }

    LOG(debug, "Will close threadpool");
    _mypool->Close();
    _executor.shutdown().sync();
    LOG(debug, "Has closed threadpool");
    _transportServer.reset();
    _engineAdapter.reset();
    _nodeManager.reset();
    _transport.reset();
    _rpc.reset();
    _mypool.reset();
}

FNET_Transport *
Fdispatch::GetFNETTransport()
{
    return _transport.get();
}

FNET_Scheduler *
Fdispatch::GetFNETScheduler()
{
    return (_transport) ? _transport->GetScheduler() : nullptr;
}

FastS_NodeManager *
Fdispatch::GetNodeManager()
{
    return _nodeManager.get();
}

FastS_DataSetCollection *
Fdispatch::GetDataSetCollection()
{
    return ( _nodeManager) ? _nodeManager->GetDataSetCollection() : nullptr;
}

FastOS_ThreadPool *
Fdispatch::GetThreadPool()
{
    return _mypool.get();
}

bool
Fdispatch::Failed()
{
    return ( (_transportServer && _transportServer->isFailed())) || _needRestart;
}

bool
Fdispatch::CheckTempFail()
{
    bool ret;
    bool failflag = _nodeManager->GetTempFail();
    unsigned int FNETLiveCounter;

    ret = true;

    FNETLiveCounter = _FNET_adapter.GetLiveCounter();
    if (FNETLiveCounter == _lastFNETLiveCounter) {
        if (_FNETLiveCounterFailed) {
            failflag = true;                // Still failure
        } else if (!_FNETLiveCounterDanger) {
            _FNETLiveCounterDanger = true;
            _FNETLiveCounterDangerStart.SetNow();
        } else if (_FNETLiveCounterDangerStart.MilliSecsToNow() >= 6000) {
            LOG(error, "fdispatch::Fdispatch::CheckTempFail: FNET inactive for 6 seconds, deadlock ?");
            _FNETLiveCounterFailed = true;      // Note that we failed
            failflag = true;                // Force temporary failure
        } else if (_FNETLiveCounterDangerStart.MilliSecsToNow() >= 3000 &&
                   !_FNETLiveCounterWarned) {
            _FNETLiveCounterWarned = true;
            LOG(warning, "fdispatch::Fdispatch::CheckTempFail: FNET inactive for 3 seconds");
        }
    } else {
        if (_FNETLiveCounterFailed || _FNETLiveCounterWarned) {
            LOG(warning, "fdispatch::Fdispatch::CheckTempFail: FNET active again");
        }
        _FNETLiveCounterFailed = false;
        _FNETLiveCounterWarned = false;
        _FNETLiveCounterDanger = false;
        _lastFNETLiveCounter = FNETLiveCounter;
    }

    if (failflag == _tempFail)
        return ret;

    if (_transportServer) {
        if (failflag) {
            _transportServer->setListen(false);
            LOG(error, "Disabling fnet server interface");
        } else {
            _transportServer->setListen(true);
            LOG(info, "Reenabling fnet server interface");
        }
    }
    _tempFail = failflag;
    return ret;
}


/**
 * Make the httpd and Monitor, and let a Thread execute each.
 * Set up stuff as specified in the fdispatch-rc-file.
 */
Fdispatch::Fdispatch(const config::ConfigUri &configUri)
    : _executor(1, 128 * 1024),
      _mypool(),
      _engineAdapter(),
      _transportServer(),
      _componentConfig(),
      _nodeManager(),
      _transport(),
      _FNET_adapter(this),
      _rpc(),
      _config(),
      _configUri(configUri),
      _fdispatchrcFetcher(configUri.getContext()),
      _rndGen(),
      _partition(0),
      _tempFail(false),
      _FNETLiveCounterDanger(false),
      _FNETLiveCounterWarned(false),
      _FNETLiveCounterFailed(false),
      _transportStarted(false),
      _lastFNETLiveCounter(false),
      _FNETLiveCounterDangerStart(),
      _timeouts(0u),
      _checkLimit(0u),
      _healthPort(0),
      _needRestart(false)
{
    int64_t cfgGen = -1;
    _config = config::ConfigGetter<FdispatchrcConfig>::
              getConfig(cfgGen, _configUri.getConfigId(), _configUri.getContext());
    LOG(config, "fdispatch version %s (RPC-port: %d, transport at %d)",
                FastS_VersionTag, _config->frtport, _config->ptport);

    _componentConfig.addConfig(vespalib::ComponentConfigProducer::Config("fdispatch", cfgGen,
                                       "config only obtained at startup"));
    _fdispatchrcFetcher.subscribe<FdispatchrcConfig>(configUri.getConfigId(), this);
    _fdispatchrcFetcher.start();
}

namespace {

bool needRestart(const FdispatchrcConfig & curr, const FdispatchrcConfig & next)
{
    if (curr.frtport != next.frtport) {
        LOG(warning, "FRT port has changed from %d to %d.", curr.frtport, next.frtport);
        return true;
    }
    if (curr.ptport != next.ptport) {
        LOG(warning, "PT port has changed from %d to %d.", curr.ptport, next.ptport);
        return true;
    }
    if (curr.healthport != next.healthport) {
        LOG(warning, "Health port has changed from %d to %d.", curr.healthport, next.healthport);
        return true;
    }
    return false;
}

}

void Fdispatch::configure(std::unique_ptr<FdispatchrcConfig> cfg)
{
    if (cfg && _config) {
        if ( needRestart(*_config, *cfg) ) {
            LOG(warning, "Will restart by abort now.");
            _needRestart.store(true);
        }
    }
}

namespace {

CompressionConfig::Type
convert(InternalFdispatchrcType::Packetcompresstype type)
{
    switch (type) {
      case InternalFdispatchrcType::LZ4: return CompressionConfig::LZ4;
      default: return CompressionConfig::LZ4;
    }
}

}

bool
Fdispatch::Init()
{
    int  maxthreads;

    _tempFail = false;
    _FNETLiveCounterDanger = false;
    _FNETLiveCounterWarned = false;
    _FNETLiveCounterFailed = false;
    _lastFNETLiveCounter = 0;
    _timeouts = 0;
    _checkLimit = 60;

    FS4PersistentPacketStreamer::Instance.SetCompressionLimit(_config->packetcompresslimit);
    FS4PersistentPacketStreamer::Instance.SetCompressionLevel(_config->packetcompresslevel);
    FS4PersistentPacketStreamer::Instance.SetCompressionType(convert(_config->packetcompresstype));
    

    LOG(debug, "Creating FNET transport");
    _transport = std::make_unique<FNET_Transport>(_config->transportthreads);

    // grab node slowness limit defaults

    FastS_DataSetDesc::SetDefaultSlowQueryLimitFactor(_config->defaultslowquerylimitfactor);
    FastS_DataSetDesc::SetDefaultSlowQueryLimitBias(_config->defaultslowquerylimitbias);
    FastS_DataSetDesc::SetDefaultSlowDocsumLimitFactor(_config->defaultslowdocsumlimitfactor);
    FastS_DataSetDesc::SetDefaultSlowDocsumLimitBias(_config->defaultslowdocsumlimitbias);

    maxthreads = _config->maxthreads;
    _mypool = std::make_unique<FastOS_ThreadPool>(256 * 1024, maxthreads);

    // Max interval betw read from socket.
    FastS_TimeOut::_val[FastS_TimeOut::maxSockSilent] = _config->maxsocksilent;

    if (_transport) {
        _transport->SetIOCTimeOut((uint32_t) (FastS_TimeOut::_val[FastS_TimeOut::maxSockSilent] * 1000.0));
    }

    char timestr[40];
    FastS_TimeOut::WriteTime(timestr, sizeof(timestr), FastS_TimeOut::_val[FastS_TimeOut::maxSockSilent]);
    LOG(debug, "VERBOSE: Max time between successful read from a socket: %s", timestr);

    FastS_QueryCacheUtil::_systemMaxHits = std::numeric_limits<int>::max();
    LOG(debug, "VERBOSE: maxhits: %d", FastS_QueryCacheUtil::_systemMaxHits);

    FastS_QueryCacheUtil::_maxOffset = std::numeric_limits<int>::max();
    const uint32_t linesize = 1;
    if (FastS_QueryCacheUtil::_systemMaxHits < linesize
        && FastS_QueryCacheUtil::_maxOffset < linesize - FastS_QueryCacheUtil::_systemMaxHits) {
        LOG(warning, "maxoffset must be >= %d! (overriding config value)", linesize - FastS_QueryCacheUtil::_systemMaxHits);
        FastS_QueryCacheUtil::_maxOffset = linesize - FastS_QueryCacheUtil::_systemMaxHits;
    }
    LOG(debug, "VERBOSE: maxoffset: %d", FastS_QueryCacheUtil::_maxOffset);

    _partition = _config->partition;

    int ptportnum = _config->ptport;

    LOG(debug, "Using port number %d", ptportnum);

    _nodeManager = std::make_unique<FastS_NodeManager>(_componentConfig, this, _partition);

    GetFNETTransport()->SetTCPNoDelay(_config->transportnodelay);
    GetFNETTransport()->SetDirectWrite(_config->transportdirectwrite);

    if (ptportnum == 0) {
        throw vespalib::IllegalArgumentException("fdispatchrc.ptportnum must be non-zero, most likely an issue with config delivery.");
    }

    _engineAdapter = std::make_unique<fdispatch::EngineAdapter>(this, _mypool.get());
    _transportServer = std::make_unique<TransportServer>(*_engineAdapter, *_engineAdapter, *_engineAdapter, ptportnum, search::engine::TransportServer::DEBUG_ALL);
    _transportServer->setTCPNoDelay(_config->transportnodelay);
    _transportServer->setDirectWrite(_config->transportdirectwrite);

    if (!_transportServer->start()) {
        _transportServer.reset();
        _engineAdapter.reset();
        LOG(error, "CRITICAL: Failed to init upwards FNET transport on port %d", ptportnum);
        return false;
    }

    _nodeManager->SubscribePartMap(_configUri);

    if (_config->frtport != 0) {
        _rpc = std::make_unique<FastS_fdispatch_RPC>(this);
        if (!_rpc->Init(_config->frtport, _configUri.getConfigId())) {
            LOG(error, "RPC init failed");
            _rpc.reset();
        }
    } else {
        _rpc.reset();
    }

    // Kick off fdispatch administrative threads.
    if (_transport) {
        _FNET_adapter.init();
        bool rc = _transport->Start(_mypool.get());
        if (rc) {
            LOG(debug, "Started FNET transport");
            _transportStarted = true;
        } else {
            LOG(error, "Failed to start FNET transport");
        }
    }
    FastOS_Thread::Sleep(1000);
    if (_rpc) {
        _rpc->Start();
    }
    _healthPort = _config->healthport;
    return true;
}


void
Fdispatch::logPerformance()
{
    _nodeManager->logPerformance(_executor);
}

uint32_t
Fdispatch::getDispatchLevel()
{
    return _config->dispatchlevel;
}
    
}
