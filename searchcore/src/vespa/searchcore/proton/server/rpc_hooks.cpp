// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_hooks.h"
#include "detached_rpc_requests_owner.h"
#include "prepare_restart_rpc_handler.h"
#include "prepare_restart2_rpc_handler.h"
#include "proton.h"
#include <vespa/searchcore/proton/flushengine/set_strategy_result.h>
#include <vespa/searchcore/proton/matchengine/matchengine.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/fnet/frt/require_capabilities.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.rtchooks");

using namespace vespalib;
using vespalib::compression::CompressionConfig;

namespace proton::experimental {

bool enable_prepare_restart2 = false;

class ConsiderEnablePrepareRestart2
{
public:
    ConsiderEnablePrepareRestart2();
};

ConsiderEnablePrepareRestart2::ConsiderEnablePrepareRestart2()
{
    const char *env = getenv("VESPA_ENABLE_PREPARE_RESTART2");
    enable_prepare_restart2 = (env != nullptr && strcmp(env, "true") == 0);
}

ConsiderEnablePrepareRestart2 consider_enable_prepare_restart2;

}

namespace {

std::string delayed_configs_string("delayedConfigs");

using Pair = std::pair<std::string, std::string>;

}

namespace proton {

void
RPCHooksBase::reportState(FRT_RPCRequest * req)
{
    std::vector<Pair> res;
    int64_t numDocs(_proton.getNumDocs());
    std::string delayedConfigs = _proton.getDelayedConfigs();

    if (_proton.getMatchEngine().isOnline()) {
        res.emplace_back("online", "true");
        res.emplace_back("onlineState", "online");
    } else {
        res.emplace_back("online", "false");
        res.emplace_back("onlineState", "onlineSoon");
    }

    res.emplace_back(delayed_configs_string, delayedConfigs);
    res.emplace_back("onlineDocs", make_string("%" PRId64, numDocs));

    FRT_Values &ret = *req->GetReturn();
    FRT_StringValue *k = ret.AddStringArray(res.size());
    FRT_StringValue *v = ret.AddStringArray(res.size());
    for (uint32_t i = 0; i < res.size(); ++i) {
        ret.SetString(&k[i], res[i].first.c_str());
        ret.SetString(&v[i], res[i].second.c_str());
    }
    for (const auto & r : res) {
        LOG(debug, "key=%s, value=%s", r.first.c_str(), r.second.c_str());
    }
    ret.AddInt32(0);
}

namespace {

std::unique_ptr<FRT_RequireCapabilities> make_proton_admin_api_capability_filter() {
    return FRT_RequireCapabilities::of(vespalib::net::tls::Capability::content_proton_admin_api());
}

}

void
RPCHooksBase::initRPC()
{
    FRT_ReflectionBuilder rb(_orb.get());
    //-------------------------------------------------------------------------
    rb.DefineMethod("pandora.rtc.getState", "ii", "SSi",
                    FRT_METHOD(RPCHooksBase::rpc_GetState), this);
    rb.MethodDesc("Get the current state of node");
    rb.ParamDesc("gencnt", "old state generation held by the client");
    rb.ParamDesc("timeout", "How many milliseconds to wait for state update");
    rb.ReturnDesc("keys", "Array of state keys");
    rb.ReturnDesc("values", "Array of state values");
    rb.ReturnDesc("newgen", "New state generation count");
    rb.RequestAccessFilter(make_proton_admin_api_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("proton.getStatus", "s", "SSSS",
                    FRT_METHOD(RPCHooksBase::rpc_GetProtonStatus), this);
    rb.MethodDesc("Get the current state of proton or a proton component");
    rb.ParamDesc("component", "Which component to check the status for");
    rb.ReturnDesc("components", "Array of component names");
    rb.ReturnDesc("states", "Array of states ");
    rb.ReturnDesc("internalStates", "Array of internal states ");
    rb.ReturnDesc("message", "Array of status messages");
    rb.RequestAccessFilter(make_proton_admin_api_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("proton.triggerFlush", "", "b",
                    FRT_METHOD(RPCHooksBase::rpc_triggerFlush), this);
    rb.MethodDesc("Tell the node to trigger flush ASAP");
    rb.ReturnDesc("success", "Whether or not a flush was triggered.");
    rb.RequestAccessFilter(make_proton_admin_api_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("proton.prepareRestart", "", "b",
                    FRT_METHOD(RPCHooksBase::rpc_prepareRestart), this);
    rb.MethodDesc("Tell the node to prepare for a restart by flushing components "
            "such that TLS replay time + time spent flushing components is as low as possible");
    rb.ReturnDesc("success", "Whether or not prepare for restart was triggered.");
    rb.RequestAccessFilter(make_proton_admin_api_capability_filter());
    //--------------------------------
    if (!experimental::enable_prepare_restart2) {
        return;
    }
    rb.DefineMethod("proton.prepareRestart2", "ii", "bs",
                    FRT_METHOD(RPCHooksBase::rpc_prepareRestart2), this);
    rb.MethodDesc("Tell the node to prepare for a restart by flushing components "
            "such that TLS replay time + time spent flushing components is as low as possible");
    rb.ParamDesc("flush_strategy_id", "Flush strategy id to wait for");
    rb.ParamDesc("timeout", "How many milliseconds to wait for flush to complete");
    rb.ReturnDesc("success", "Whether or not prepare for restart was triggered.");
    rb.ReturnDesc("state", "State for ongoing and last completed flush.");
    rb.RequestAccessFilter(make_proton_admin_api_capability_filter());
}

RPCHooksBase::Params::Params(Proton &parent, uint32_t port, const config::ConfigUri & configUri,
                             std::string_view slobrokId, uint32_t transportThreads)
    : proton(parent),
      slobrok_config(configUri.createWithNewId(std::string(slobrokId))),
      identity(configUri.getConfigId()),
      rtcPort(port),
      numTranportThreads(transportThreads)
{ }

RPCHooksBase::Params::~Params() = default;

RPCHooksBase::RPCHooksBase(Params &params)
    : _proton(params.proton),
      _transport(std::make_unique<FNET_Transport>(params.numTranportThreads)),
      _detached_requests_owner(std::make_shared<DetachedRpcRequestsOwner>()),
      _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
      _proto_rpc_adapter(std::make_unique<ProtoRpcAdapter>(
                      _proton.get_search_server(),
                      _proton.get_docsum_server(),
                      _proton.get_monitor_server(), *_orb)),
      _regAPI(*_orb, slobrok::ConfiguratorFactory(params.slobrok_config))
{ }

void
RPCHooksBase::open(Params & params)
{
    initRPC();
    _regAPI.registerName((params.identity + "/realtimecontroller").c_str());
    _orb->Listen(params.rtcPort);
    _transport->Start();
    LOG(debug, "started monitoring interface");
}

void
RPCHooksBase::set_online()
{
    _proto_rpc_adapter->set_online();
}

RPCHooksBase::~RPCHooksBase() = default;

void
RPCHooksBase::close()
{
    LOG(info, "shutting down monitoring interface");
    _detached_requests_owner->close(); // Abort detached requests before shutting down transport
    _transport->ShutDown(true);
    // Proton destructor drains tasks scheduled on proton executor before destroying _rpcHooks.
}

void
RPCHooksBase::letProtonDo(Executor::Task::UP task)
{
    _proton.getExecutor().execute(std::move(task));
}

void
RPCHooksBase::triggerFlush(FRT_RPCRequest *req)
{
    if (experimental::enable_prepare_restart2) {
        LOG(info, "RPCHooksBase::triggerFlush will create experimental detached rpc handler");
        std::chrono::steady_clock::duration timeout(610s); // 10s greater timeout than the one in vespa-proton-cmd
        auto set_strategy_result = _proton.trigger_flush2();
        using TriggerFlushRpcHandler = PrepareRestartRpcHandler;
        using RefCountedRpcRequest = vespalib::ref_counted<FRT_RPCRequest>;
        auto handler = std::make_shared<TriggerFlushRpcHandler>(_detached_requests_owner,
                                                                RefCountedRpcRequest::internal_attach(req),
                                                                set_strategy_result.lowest_strategy_id_notifier(),
                                                                _transport->GetScheduler(),
                                                                set_strategy_result.wait_strategy_id(),
                                                                timeout);
        handler->setup();
        return;
    }
    if (_proton.triggerFlush()) {
        req->GetReturn()->AddInt8(1);
        LOG(info, "RPCHooksBase::Flush finished successfully");
    } else {
        req->GetReturn()->AddInt8(0);
        LOG(warning, "RPCHooksBase::Flush failed");
    }
    req->Return();
}

void
RPCHooksBase::prepareRestart(FRT_RPCRequest *req)
{
    if (experimental::enable_prepare_restart2) {
        LOG(info, "RPCHooksBase::prepareRestart will create experimental detached rpc handler");
        std::chrono::steady_clock::duration timeout(610s); // 10s greater timeout than the one in vespa-proton-cmd
        auto set_strategy_result = _proton.prepare_restart2(0);
        using RefCountedRpcRequest = vespalib::ref_counted<FRT_RPCRequest>;
        auto handler = std::make_shared<PrepareRestartRpcHandler>(_detached_requests_owner,
                                                                  RefCountedRpcRequest::internal_attach(req),
                                                                  set_strategy_result.lowest_strategy_id_notifier(),
                                                                  _transport->GetScheduler(),
                                                                  set_strategy_result.wait_strategy_id(),
                                                                  timeout);
        handler->setup();
        return;
    }
    if (_proton.prepareRestart()) {
        req->GetReturn()->AddInt8(1);
        LOG(info, "RPCHooksBase::prepareRestart finished successfully");
    } else {
        req->GetReturn()->AddInt8(0);
        LOG(warning, "RPCHooksBase::prepareRestart failed");
    }
    req->Return();
}

void
RPCHooksBase::prepareRestart2(FRT_RPCRequest *req)
{
    LOG(info, "RPCHooksBase::prepareRestart2 started");

    FRT_Values &arg = *req->GetParams();
    uint32_t wait_strategy_id = arg[0]._intval32;
    std::chrono::steady_clock::duration timeout = std::chrono::milliseconds(arg[1]._intval32);

    auto set_strategy_result = _proton.prepare_restart2(wait_strategy_id);
    using RefCountedRpcRequest = vespalib::ref_counted<FRT_RPCRequest>;
    auto handler = std::make_shared<PrepareRestart2RpcHandler>(_detached_requests_owner,
                                                               RefCountedRpcRequest::internal_attach(req),
                                                               set_strategy_result.lowest_strategy_id_notifier(),
                                                               _transport->GetScheduler(),
                                                               set_strategy_result.wait_strategy_id(),
                                                               timeout,
                                                               set_strategy_result.flush_history());
    handler->setup();
}

void
RPCHooksBase::rpc_GetState(FRT_RPCRequest *req)
{
    FRT_Values &arg = *req->GetParams();
    uint32_t gen = arg[0]._intval32;
    uint32_t timeoutMS = arg[1]._intval32;
    LOG(debug, "RPCHooksBase::rpc_GetState(gen=%d, timeoutMS=%d)", gen, timeoutMS);

    reportState(req);
}

void
RPCHooksBase::rpc_GetProtonStatus(FRT_RPCRequest *req)
{
    LOG(debug, "RPCHooksBase::rpc_GetProtonStatus started");
    req->Detach();
    letProtonDo(makeLambdaTask([this, req]() { getProtonStatus(req); }));
}

void
RPCHooksBase::getProtonStatus(FRT_RPCRequest *req)
{
    StatusReport::List reports(_proton.getStatusReports());
    FRT_Values &ret = *req->GetReturn();
    FRT_StringValue *r = ret.AddStringArray(reports.size());
    FRT_StringValue *k = ret.AddStringArray(reports.size());
    FRT_StringValue *internalStates = ret.AddStringArray(reports.size());
    FRT_StringValue *v = ret.AddStringArray(reports.size());
    for (uint32_t i = 0; i < reports.size(); ++i) {
        const StatusReport & report = *reports[i];
        ret.SetString(&r[i], report.getComponent().c_str());
        switch (report.getState()) {
        case StatusReport::UPOK:
            ret.SetString(&k[i], "OK");
            break;
        case StatusReport::PARTIAL:
            ret.SetString(&k[i], "WARNING");
            break;
        case StatusReport::DOWN:
            ret.SetString(&k[i], "CRITICAL");
            break;
        default:
            ret.SetString(&k[i], "UNKNOWN");
            break;
        }
        ret.SetString(&internalStates[i], report.getInternalStatesStr().c_str());
        ret.SetString(&v[i], report.getMessage().c_str());
        LOG(debug, "component(%s), status(%s), internalState(%s), message(%s)",
                     report.getComponent().c_str(), k[i]._str, internalStates[i]._str, report.getMessage().c_str());
    }
    req->Return();
}

void
RPCHooksBase::rpc_triggerFlush(FRT_RPCRequest *req)
{
    LOG(info, "RPCHooksBase::rpc_triggerFlush started");
    req->Detach();
    letProtonDo(makeLambdaTask([this, req]() { triggerFlush(req); }));
}

void
RPCHooksBase::rpc_prepareRestart(FRT_RPCRequest *req)
{
    LOG(info, "RPCHooksBase::rpc_prepareRestart started");
    req->Detach();
    letProtonDo(makeLambdaTask([this, req]() { prepareRestart(req); }));
}

void
RPCHooksBase::rpc_prepareRestart2(FRT_RPCRequest *req)
{
    LOG(info, "RPCHooksBase::rpc_prepareRestart2 started");
    req->Detach();
    letProtonDo(makeLambdaTask([this, req]() { prepareRestart2(req); }));
}

RPCHooks::RPCHooks(Params &params)
    : RPCHooksBase(params)
{
    open(params);
}

}
