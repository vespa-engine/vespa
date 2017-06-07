// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sbregister.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

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
discard(std::vector<vespalib::string> &vec, const vespalib::stringref & val)
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

} // namespace <unnamed>

namespace slobrok::api {

RegisterAPI::RegisterAPI(FRT_Supervisor &orb, const ConfiguratorFactory & config)
    : FNET_Task(orb.GetScheduler()),
      _orb(orb),
      _hooks(*this),
      _lock(),
      _reqDone(false),
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
    LOG_ASSERT(_slobrokSpecs.ok());
    ScheduleNow();
}


RegisterAPI::~RegisterAPI()
{
    Kill();
    _configurator.reset(0);
    if (_req != 0) {
        _req->Abort();
        _req->SubRef();
    }
    if (_target != 0) {
        _target->SubRef();
    }
}


void
RegisterAPI::registerName(const vespalib::stringref & name)
{
    _lock.Lock();
    for (uint32_t i = 0; i < _names.size(); ++i) {
        if (_names[i] == name) {
            _lock.Unlock();
            return;
        }
    }
    _busy = true;
    _names.push_back(name);
    _pending.push_back(name);
    discard(_unreg, name);
    ScheduleNow();
    _lock.Unlock();
}


void
RegisterAPI::unregisterName(const vespalib::stringref & name)
{
    _lock.Lock();
    _busy = true;
    discard(_names, name);
    discard(_pending, name);
    _unreg.push_back(name);
    ScheduleNow();
    _lock.Unlock();
}

// handle any request that completed
void
RegisterAPI::handleReqDone()
{
    if (_reqDone) {
        _reqDone = false;
        if (_req->IsError()) {
            if (_req->GetErrorCode() != FRTE_RPC_METHOD_FAILED) {
                LOG(debug, "register failed: %s (code %d)",
                    _req->GetErrorMessage(), _req->GetErrorCode());
                // unexpected error; close our connection to this
                // slobrok server and try again with a fresh slate
                if (_target != 0) {
                    _target->SubRef();
                }
                _target = 0;
                _busy = true;
            } else {
                LOG(warning, "%s(%s -> %s) failed: %s",
                    _req->GetMethodName(),
                    (*_req->GetParams())[0]._string._str,
                    (*_req->GetParams())[1]._string._str,
                    _req->GetErrorMessage());
            }
        } else {
            // reset backoff strategy on any successful request
            _backOff.reset();
        }
        _req->SubRef();
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
            LOG(warning, "current server %s not in list of location brokers: %s",
                _currSlobrok.c_str(), cps.c_str());
            _target->SubRef();
            _target = 0;
        }
    }
    if (_target == 0) {
        _currSlobrok = _slobrokSpecs.nextSlobrokSpec();
        if (_currSlobrok.size() > 0) {
            // try next possible server.
            _target = _orb.GetTarget(_currSlobrok.c_str());
        }
        _lock.Lock();
        // now that we have a new connection, we need to
        // immediately re-register everything.
        _pending = _names;
        _lock.Unlock();
        if (_target == 0) {
            // we have tried all possible servers.
            // start from the top after a delay,
            // possibly with a warning.
            double delay = _backOff.get();
            Schedule(delay);
            if (_backOff.shouldWarn()) {
                vespalib::string cps = _slobrokSpecs.logString();
                LOG(warning, "cannot connect to location broker at %s "
                    "(retry in %f seconds)", cps.c_str(), delay);
            } else {
                LOG(debug, "slobrok retry in %f seconds", delay);
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
    _lock.Lock();
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
    _lock.Unlock();

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
        _lock.Lock();
        _pending = _names;
        LOG(debug, "done, reschedule in 30s");
        _busy = false;
        Schedule(30.0);
        _lock.Unlock();
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
    LOG_ASSERT(req == _req && !_reqDone);
    (void) req;
    _reqDone = true;
    ScheduleNow();
}

//-----------------------------------------------------------------------------

RegisterAPI::RPCHooks::RPCHooks(RegisterAPI &owner)
    : _owner(owner)
{
    FRT_ReflectionBuilder rb(&_owner._orb);
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.listNamesServed", "", "S", true,
                    FRT_METHOD(RPCHooks::rpc_listNamesServed), this);
    rb.MethodDesc("List rpcserver names");
    rb.ReturnDesc("names", "The rpcserver names this server wants to serve");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.notifyUnregistered", "s", "", true,
                    FRT_METHOD(RPCHooks::rpc_notifyUnregistered), this);
    rb.MethodDesc("Notify a server about removed registration");
    rb.ParamDesc("name", "RpcServer name");
    //-------------------------------------------------------------------------
}


RegisterAPI::RPCHooks::~RPCHooks()
{
}


void
RegisterAPI::RPCHooks::rpc_listNamesServed(FRT_RPCRequest *req)
{
    FRT_Values &dst = *req->GetReturn();
    _owner._lock.Lock();
    FRT_StringValue *names = dst.AddStringArray(_owner._names.size());
    for (uint32_t i = 0; i < _owner._names.size(); ++i) {
        dst.SetString(&names[i], _owner._names[i].c_str());
    }
    _owner._lock.Unlock();
}


void
RegisterAPI::RPCHooks::rpc_notifyUnregistered(FRT_RPCRequest *req)
{
    FRT_Values &args = *req->GetParams();
    LOG(warning, "unregistered name %s", args[0]._string._str);
}

}
