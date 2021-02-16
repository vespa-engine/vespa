// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_hooks.h"
#include "proton.h"
#include <vespa/searchcore/proton/summaryengine/docsum_by_slime.h>
#include <vespa/searchcore/proton/matchengine/matchengine.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.rtchooks");

using namespace vespalib;
using vespalib::compression::CompressionConfig;

namespace {

using Pair = std::pair<string, string>;

VESPA_THREAD_STACK_TAG(proton_rpc_executor)

}

namespace proton {

void
RPCHooksBase::checkState(std::unique_ptr<StateArg> arg)
{
    steady_time now(steady_clock::now());
    if (now < arg->_dueTime) {
        std::unique_lock<std::mutex> guard(_stateLock);
        vespalib::duration left = (arg->_dueTime - now);
        if (_stateCond.wait_for(guard, left) == std::cv_status::no_timeout) {
            LOG(debug, "state has changed");
            reportState(*arg->_session, arg->_req);
            arg->_req->Return();
        } else {
            LOG(debug, "Will reschedule");
            FRT_RPCRequest * req = arg->_req;
            Session & session = *arg->_session;
            Executor::Task::UP failedTask = _executor.execute(makeLambdaTask([this, arg=std::move(arg)]() mutable {
                checkState(std::move(arg));
            }));
            if (failedTask) {
                reportState(session, req);
                req->Return();
            }
        }
    } else {
        reportState(*arg->_session, arg->_req);
        arg->_req->Return();
    }
}

void
RPCHooksBase::reportState(Session & session, FRT_RPCRequest * req)
{
    std::vector<Pair> res;
    int64_t numDocs(_proton.getNumDocs());
    std::string delayedConfigs = _proton.getDelayedConfigs();
    bool numDocsChanged = session.getNumDocs() != numDocs;
    bool delayedConfigsChanged = session.getDelayedConfigs() != delayedConfigs;
    bool changed = numDocsChanged || delayedConfigsChanged;

    if (_proton.getMatchEngine().isOnline()) {
        res.emplace_back("online", "true");
        res.emplace_back("onlineState", "online");
    } else {
        res.emplace_back("online", "false");
        res.emplace_back("onlineState", "onlineSoon");
    }
    if (session.getGen() < 0) {
        if (delayedConfigsChanged) {
            res.emplace_back("delayedConfigs", delayedConfigs);
        }
        res.emplace_back("onlineDocs", make_string("%" PRId64, numDocs));
        session.setGen(0);
    } else if (changed) {
        if (delayedConfigsChanged) {
            res.emplace_back("delayedConfigs", delayedConfigs);
        }
        res.emplace_back("onlineDocs", make_string("%" PRId64, numDocs));
        session.setGen(session.getGen() + 1);
    }
    if (numDocsChanged)
        session.setNumDocs(numDocs);
    if (delayedConfigsChanged)
        session.setDelayedConfigs(delayedConfigs);

    FRT_Values &ret = *req->GetReturn();
    FRT_StringValue *k = ret.AddStringArray(res.size());
    FRT_StringValue *v = ret.AddStringArray(res.size());
    for (uint32_t i = 0; i < res.size(); ++i) {
        ret.SetString(&k[i], res[i].first.c_str());
        ret.SetString(&v[i], res[i].second.c_str());
    }
    LOG(debug, "gen=%" PRId64, session.getGen());
    for (const auto & r : res) {
        LOG(debug, "key=%s, value=%s", r.first.c_str(), r.second.c_str());
    }
    ret.AddInt32(session.getGen());
}

RPCHooksBase::Session::Session()
    : _numDocs(0u),
      _delayedConfigs(),
      _gen(-1),
      _down(false)
{
}

void
RPCHooksBase::initRPC()
{
    _orb->SetSessionInitHook(FRT_METHOD(RPCHooksBase::initSession), this);
    _orb->SetSessionFiniHook(FRT_METHOD(RPCHooksBase::finiSession), this);
    _orb->SetSessionDownHook(FRT_METHOD(RPCHooksBase::downSession), this);
    _orb->SetMethodMismatchHook(FRT_METHOD(RPCHooksBase::mismatch), this);

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
    //-------------------------------------------------------------------------
    rb.DefineMethod("proton.getStatus", "s", "SSSS",
                    FRT_METHOD(RPCHooksBase::rpc_GetProtonStatus), this);
    rb.MethodDesc("Get the current state of proton or a proton component");
    rb.ParamDesc("component", "Which component to check the status for");
    rb.ReturnDesc("components", "Array of component names");
    rb.ReturnDesc("states", "Array of states ");
    rb.ReturnDesc("internalStates", "Array of internal states ");
    rb.ReturnDesc("message", "Array of status messages");
    //-------------------------------------------------------------------------
    rb.DefineMethod("pandora.rtc.getIncrementalState", "i", "SSi",
                    FRT_METHOD(RPCHooksBase::rpc_getIncrementalState), this);
    rb.MethodDesc("Get node state changes since last invocation");
    rb.ParamDesc("timeout", "How many milliseconds to wait for state update");
    rb.ReturnDesc("keys", "Array of state keys");
    rb.ReturnDesc("values", "Array of state values");
    rb.ReturnDesc("dummy", "Dummy value to enable code reuse");
    //-------------------------------------------------------------------------
    rb.DefineMethod("pandora.rtc.shutdown", "", "",
                    FRT_METHOD(RPCHooksBase::rpc_Shutdown), this);
    rb.MethodDesc("Shut down the rtc application");
    //-------------------------------------------------------------------------
    rb.DefineMethod("pandora.rtc.die", "", "",
                    FRT_METHOD(RPCHooksBase::rpc_die), this);
    rb.MethodDesc("Exit the rtc application without cleanup");
    //-------------------------------------------------------------------------
    rb.DefineMethod("proton.triggerFlush", "", "b",
                    FRT_METHOD(RPCHooksBase::rpc_triggerFlush), this);
    rb.MethodDesc("Tell the node to trigger flush ASAP");
    rb.ReturnDesc("success", "Whether or not a flush was triggered.");
    //-------------------------------------------------------------------------
    rb.DefineMethod("proton.prepareRestart", "", "b",
                    FRT_METHOD(RPCHooksBase::rpc_prepareRestart), this);
    rb.MethodDesc("Tell the node to prepare for a restart by flushing components "
            "such that TLS replay time + time spent flushing components is as low as possible");
    rb.ReturnDesc("success", "Whether or not prepare for restart was triggered.");
    //-------------------------------------------------------------------------
    rb.DefineMethod("proton.getDocsums", "bix", "bix", FRT_METHOD(RPCHooksBase::rpc_getDocSums), this);
    rb.MethodDesc("Get list of document summaries");
    rb.ParamDesc("encoding", "0=raw, 6=lz4");
    rb.ParamDesc("uncompressedBlobSize", "Uncompressed blob size");
    rb.ParamDesc("getDocsumX", "The request blob in slime");
    rb.ReturnDesc("encoding",  "0=raw, 6=lz4");
    rb.ReturnDesc("uncompressedBlobSize", "Uncompressed blob size");
    rb.ReturnDesc("docsums", "Blob with slime encoded summaries.");
    
}

RPCHooksBase::Params::Params(Proton &parent, uint32_t port, const vespalib::string &ident, uint32_t numThreads)
    : proton(parent),
      slobrok_config(config::ConfigUri("admin/slobrok.0")),
      identity(ident),
      rtcPort(port),
      numRpcThreads(numThreads)
{ }

RPCHooksBase::Params::~Params() = default;

RPCHooksBase::RPCHooksBase(Params &params)
    : _proton(params.proton),
      _docsumByRPC(std::make_unique<DocsumByRPC>(_proton.getDocsumBySlime())),
      _transport(std::make_unique<FNET_Transport>(2)),
      _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
      _proto_rpc_adapter(std::make_unique<ProtoRpcAdapter>(
                      _proton.get_search_server(),
                      _proton.get_docsum_server(),
                      _proton.get_monitor_server(), *_orb)),
      _regAPI(*_orb, slobrok::ConfiguratorFactory(params.slobrok_config)),
      _stateLock(),
      _stateCond(),
      _executor(params.numRpcThreads, 128_Ki, proton_rpc_executor)
{ }

void
RPCHooksBase::open(Params & params)
{
    initRPC();
    _regAPI.registerName((params.identity + "/realtimecontroller").c_str());
    _orb->Listen(params.rtcPort);
    _transport->Start(&_proton.getThreadPool());
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
    _transport->ShutDown(true);
    _executor.shutdown();
    {
        std::lock_guard<std::mutex> guard(_stateLock);
        _stateCond.notify_all();
    }
    _executor.sync();
}

void
RPCHooksBase::letProtonDo(Executor::Task::UP task)
{
    _proton.getExecutor().execute(std::move(task));
}

void
RPCHooksBase::triggerFlush(FRT_RPCRequest *req)
{
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
RPCHooksBase::rpc_GetState(FRT_RPCRequest *req)
{
    FRT_Values &arg = *req->GetParams();
    uint32_t gen = arg[0]._intval32;
    uint32_t timeoutMS = arg[1]._intval32;
    const Session::SP & sharedSession = getSession(req);
    LOG(debug, "RPCHooksBase::rpc_GetState(gen=%d, timeoutMS=%d) , but using gen=%" PRId64 " instead",
               gen, timeoutMS, sharedSession->getGen());

    int64_t numDocs(_proton.getNumDocs());
    if (sharedSession->getGen() < 0 || sharedSession->getNumDocs() != numDocs) {  // NB Should use something else to define generation.
        reportState(*sharedSession, req);
    } else {
        steady_time dueTime(steady_clock::now() + std::chrono::milliseconds(timeoutMS));
        auto failed = _executor.execute(makeLambdaTask([this, arg=std::make_unique<StateArg>(sharedSession, req, dueTime)]() mutable {
            checkState(std::move(arg));
        }));
        if (failed) {
            reportState(*sharedSession, req);
            req->Return();
        } else {
            req->Detach();
        }
    }
}

void
RPCHooksBase::rpc_GetProtonStatus(FRT_RPCRequest *req)
{
    LOG(debug, "RPCHooksBase::rpc_GetProtonStatus started");
    req->Detach();
    _executor.execute(makeLambdaTask([this, req]() { getProtonStatus(req); }));
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
RPCHooksBase::rpc_getIncrementalState(FRT_RPCRequest *req)
{
    FRT_Values &arg = *req->GetParams();
    uint32_t timeoutMS = arg[0]._intval32;
    const Session::SP & sharedSession = getSession(req);
    LOG(debug, "RPCHooksBase::rpc_getIncrementalState(timeoutMS=%d)", timeoutMS);

    int64_t numDocs(_proton.getNumDocs());
    if (sharedSession->getGen() < 0 || sharedSession->getNumDocs() != numDocs) {  // NB Should use something else to define generation.
        reportState(*sharedSession, req);
    } else {
        steady_time dueTime(steady_clock::now() + std::chrono::milliseconds(timeoutMS));
        auto stateArg = std::make_unique<StateArg>(sharedSession, req, dueTime);
        if (_executor.execute(makeLambdaTask([this, arg=std::move(stateArg)]() mutable { checkState(std::move(arg)); }))) {
            reportState(*sharedSession, req);
            req->Return();
        } else {
            req->Detach();
        }
    }
}

void
RPCHooksBase::rpc_Shutdown(FRT_RPCRequest *req)
{
    (void) req;
    LOG(debug, "RPCHooksBase::rpc_Shutdown");
}

void
RPCHooksBase::rpc_die(FRT_RPCRequest *req)
{
    (void) req;
    LOG(debug, "RPCHooksBase::rpc_die");
    _exit(0);
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
RPCHooksBase::rpc_getDocSums(FRT_RPCRequest *req)
{
    LOG(debug, "proton.getDocsums()");
    req->Detach();
    _executor.execute(makeLambdaTask([this, req]() { getDocsums(req); }));
}

void
RPCHooksBase::getDocsums(FRT_RPCRequest *req)
{
    _docsumByRPC->getDocsums(*req);
    req->Return();
}

const RPCHooksBase::Session::SP &
RPCHooksBase::getSession(FRT_RPCRequest *req)
{
    FNET_Connection *conn = req->GetConnection();
    void *vctx = conn->GetContext()._value.VOIDP;
    auto *sessionspp = static_cast<Session::SP *>(vctx);
    return *sessionspp;
}

void
RPCHooksBase::initSession(FRT_RPCRequest *req)
{
    FNET_Connection *conn = req->GetConnection();
    const void * voidp(conn->GetContext()._value.VOIDP);
    req->GetConnection()->SetContext(new Session::SP(new Session()));
    void *vctx = conn->GetContext()._value.VOIDP;
    LOG(debug, "RPCHooksBase::iniSession req=%p connection=%p session=%p oldSession=%p", req, conn, vctx, voidp);
}

void
RPCHooksBase::finiSession(FRT_RPCRequest *req)
{
    FNET_Connection *conn = req->GetConnection();
    void *vctx = conn->GetContext()._value.VOIDP;
    conn->GetContextPT()->_value.VOIDP = nullptr;
    LOG(debug, "RPCHooksBase::finiSession req=%p connection=%p session=%p", req, conn, vctx);

    auto * sessionspp = static_cast<Session::SP *>(vctx);
    delete sessionspp;
}

void
RPCHooksBase::downSession(FRT_RPCRequest *req)
{
    LOG(debug, "RPCHooksBase::downSession req=%p connection=%p session=%p",
               req, req->GetConnection(), req->GetConnection()->GetContext()._value.VOIDP);
    getSession(req)->setDown();
}

void
RPCHooksBase::mismatch(FRT_RPCRequest *req)
{
    LOG(warning, "RPC Mismatch: %s, (%d): %s", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
}

RPCHooks::RPCHooks(Params &params) :
    RPCHooksBase(params)
{
    open(params);
}

}
