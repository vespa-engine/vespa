// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sbregister.h"
#include <vespa/fnet/frt/require_capabilities.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.register");

using vespalib::NetworkSetupFailureException;

namespace {

vespalib::string
createSpec(FRT_Supervisor &orb)
{
    vespalib::string spec;
    if (orb.GetListenPort() != 0) {
        vespalib::asciistream str;
        str << "tcp/";
        str << vespalib::HostName::get();
        str << ":";
        str << orb.GetListenPort();
        spec = str.str();
    }
    return spec;
}


void
discard(std::vector<vespalib::string> &vec, vespalib::stringref val)
{
    uint32_t i = 0;
    uint32_t size = vec.size();
    while (i < size) {
        if (vec[i] == val) {
            std::swap(vec[i], vec[size - 1]);
            vec.pop_back();
            --size;
        } else {
            ++i;
        }
    }
    LOG_ASSERT(size == vec.size());
}

std::unique_ptr<FRT_RequireCapabilities> make_slobrok_capability_filter() {
    return FRT_RequireCapabilities::of(vespalib::net::tls::Capability::slobrok_api());
}

} // namespace <unnamed>

namespace slobrok::api {

RegisterAPI::RegisterAPI(FRT_Supervisor &orb, const ConfiguratorFactory & config)
    : FNET_Task(orb.GetScheduler()),
      _orb(orb),
      _hooks(*this),
      _lock(),
      _reqDone(false),
      _logOnSuccess(true),
      _busy(false),
      _slobrokSpecs(),
      _configurator(config.create(_slobrokSpecs)),
      _currSlobrok(""),
      _idx(0),
      _backOff(),
      _names(),
      _pending(),
      _unreg(),
      _target(0),
      _req(0)
{
    _configurator->poll();
    if ( ! _slobrokSpecs.ok()) {
        throw NetworkSetupFailureException("Failed configuring the RegisterAPI. No valid slobrok specs from config",
                                           VESPA_STRLOC);
    }
    ScheduleNow();
}


RegisterAPI::~RegisterAPI()
{
    Kill();
    _configurator.reset(0);
    if (_req != 0) {
        _req->Abort();
        _req->internal_subref();
    }
    if (_target != 0) {
        _target->internal_subref();
    }
}


void
RegisterAPI::registerName(vespalib::stringref name)
{
    std::lock_guard<std::mutex> guard(_lock);
    for (uint32_t i = 0; i < _names.size(); ++i) {
        if (_names[i] == name) {
            return;
        }
    }
    _busy.store(true, std::memory_order_relaxed);
    _names.push_back(name);
    _pending.push_back(name);
    discard(_unreg, name);
    ScheduleNow();
}


void
RegisterAPI::unregisterName(vespalib::stringref name)
{
    std::lock_guard<std::mutex> guard(_lock);
    _busy.store(true, std::memory_order_relaxed);
    discard(_names, name);
    discard(_pending, name);
    _unreg.push_back(name);
    ScheduleNow();
}

// handle any request that completed
void
RegisterAPI::handleReqDone()
{
    if (_reqDone.load(std::memory_order_relaxed)) {
        _reqDone.store(false, std::memory_order_relaxed);
        if (_req->IsError()) {
            if (_req->GetErrorCode() != FRTE_RPC_METHOD_FAILED) {
                LOG(debug, "register failed: %s (code %d)",
                    _req->GetErrorMessage(), _req->GetErrorCode());
                // unexpected error; close our connection to this
                // slobrok server and try again with a fresh slate
                if (_target != 0) {
                    _target->internal_subref();
                }
                _target = 0;
                _busy.store(true, std::memory_order_relaxed);
            } else {
                LOG(warning, "%s(%s -> %s) failed: %s",
                    _req->GetMethodName(),
                    (*_req->GetParams())[0]._string._str,
                    (*_req->GetParams())[1]._string._str,
                    _req->GetErrorMessage());
            }
        } else {
            if (_logOnSuccess && (_pending.size() == 0) && (_names.size() > 0)) {
                LOG(info, "[RPC @ %s] registering %s with location broker %s completed successfully",
                    createSpec(_orb).c_str(), _names[0].c_str(), _currSlobrok.c_str());
                _logOnSuccess = false;
            }
            // reset backoff strategy on any successful request
            _backOff.reset();
        }
        _req->internal_subref();
        _req = 0;
    }
}

// do we need to try to reconnect?
void
RegisterAPI::handleReconnect()
{
    if (_configurator->poll() && _target != 0) {
        if (! _slobrokSpecs.contains(_currSlobrok)) {
            vespalib::string cps = _slobrokSpecs.logString();
            LOG(warning, "[RPC @ %s] location broker %s removed, will disconnect and use one of: %s",
                createSpec(_orb).c_str(), _currSlobrok.c_str(), cps.c_str());
            _target->internal_subref();
            _target = 0;
        }
    }
    if (_target == 0) {
        _logOnSuccess = true;
        _currSlobrok = _slobrokSpecs.nextSlobrokSpec();
        if (_currSlobrok.size() > 0) {
            // try next possible server.
            _target = _orb.GetTarget(_currSlobrok.c_str());
        }
        {
            std::lock_guard<std::mutex> guard(_lock);
            // now that we have a new connection, we need to
            // immediately re-register everything.
            _pending = _names;
        }
        if (_target == 0) {
            // we have tried all possible servers.
            // start from the top after a delay,
            // possibly with a warning.
            double delay = _backOff.get();
            Schedule(delay);
            const char * const msgfmt = "[RPC @ %s] no location brokers available, retrying: %s (in %.1f seconds)";
            vespalib::string cps = _slobrokSpecs.logString();
            if (_backOff.shouldWarn()) {
                LOG(warning, msgfmt, createSpec(_orb).c_str(), cps.c_str(), delay);
            } else {
                LOG(debug, msgfmt, createSpec(_orb).c_str(), cps.c_str(), delay);
            }
            return;
        }
    }
}

// perform any unregister or register that is pending
void
RegisterAPI::handlePending()
{
    bool unreg = false;
    bool reg   = false;
    vespalib::string name;
    {
        std::lock_guard<std::mutex> guard(_lock);
        // pop off the todo stack, unregister has priority
        if (_unreg.size() > 0) {
            name = _unreg.back();
            _unreg.pop_back();
            unreg = true;
        } else if (_pending.size() > 0) {
            name = _pending.back();
            _pending.pop_back();
            reg = true;
        }
    }

    if (unreg) {
        // start a new unregister request
        LOG_ASSERT(!reg);
        _req = _orb.AllocRPCRequest();
        _req->SetMethodName("slobrok.unregisterRpcServer");
        _req->GetParams()->AddString(name.c_str());
        LOG(debug, "unregister [%s]", name.c_str());
        _req->GetParams()->AddString(createSpec(_orb).c_str());
        _target->InvokeAsync(_req, 35.0, this);
    } else if (reg) {
        // start a new register request
        _req = _orb.AllocRPCRequest();
        _req->SetMethodName("slobrok.registerRpcServer");
        _req->GetParams()->AddString(name.c_str());
        LOG(debug, "register [%s]", name.c_str());
        _req->GetParams()->AddString(createSpec(_orb).c_str());
        _target->InvokeAsync(_req, 35.0, this);
    } else {
        // nothing more to do right now; schedule to re-register all
        // names after a long delay.
        std::lock_guard<std::mutex> guard(_lock);
        _pending = _names;
        LOG(debug, "done, reschedule in 30s");
        _busy.store(false, std::memory_order_relaxed);
        Schedule(30.0);
    }
}

void
RegisterAPI::PerformTask()
{
    handleReqDone();
    if (_req != 0) {
        LOG(debug, "req in progress");
        return; // current request still in progress, don't start anything new
    }
    handleReconnect();
    // still no connection?
    if (_target == 0) return;
    handlePending();
}


void
RegisterAPI::RequestDone(FRT_RPCRequest *req)
{
    LOG_ASSERT(req == _req && !_reqDone.load(std::memory_order_relaxed));
    (void) req;
    _reqDone.store(true, std::memory_order_relaxed);
    ScheduleNow();
}

//-----------------------------------------------------------------------------

RegisterAPI::RPCHooks::RPCHooks(RegisterAPI &owner)
    : _owner(owner)
{
    FRT_ReflectionBuilder rb(&_owner._orb);
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.listNamesServed", "", "S",
                    FRT_METHOD(RPCHooks::rpc_listNamesServed), this);
    rb.MethodDesc("List rpcserver names");
    rb.ReturnDesc("names", "The rpcserver names this server wants to serve");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.notifyUnregistered", "s", "",
                    FRT_METHOD(RPCHooks::rpc_notifyUnregistered), this);
    rb.MethodDesc("Notify a server about removed registration");
    rb.ParamDesc("name", "RpcServer name");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
}


RegisterAPI::RPCHooks::~RPCHooks()
{
}


void
RegisterAPI::RPCHooks::rpc_listNamesServed(FRT_RPCRequest *req)
{
    FRT_Values &dst = *req->GetReturn();
    std::lock_guard<std::mutex> guard(_owner._lock);
    FRT_StringValue *names = dst.AddStringArray(_owner._names.size());
    for (uint32_t i = 0; i < _owner._names.size(); ++i) {
        dst.SetString(&names[i], _owner._names[i].c_str());
    }
}


void
RegisterAPI::RPCHooks::rpc_notifyUnregistered(FRT_RPCRequest *req)
{
    FRT_Values &args = *req->GetParams();
    LOG(warning, "unregistered name %s", args[0]._string._str);
}

}
