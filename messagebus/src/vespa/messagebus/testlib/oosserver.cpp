// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "oosserver.h"
#include "slobrok.h"

namespace mbus {

OOSServer::OOSServer(const Slobrok &slobrok, const string service,
                     const OOSState &state)
    : _lock("mbus::OOSServer::_lock", false),
      _orb(),
      _port(0),
      _regAPI(_orb, slobrok::ConfiguratorFactory(slobrok.config())),
      _genCnt(1),
      _state()
{
    setState(state);
    {
        FRT_ReflectionBuilder rb(&_orb);
        //-------------------------------------------------------------------
        rb.DefineMethod("fleet.getOOSList", "ii", "Si", true,
                        FRT_METHOD(OOSServer::rpc_poll), this);
        rb.MethodDesc("fetch OOS information");
        rb.ParamDesc("gencnt", "generation already known by client");
        rb.ParamDesc("timeout", "How many milliseconds to wait for changes "
                 "before returning if nothing has changed (max=10000)");
        rb.ReturnDesc("names", "list of services that are OOS "
                      "(empty if generation has not changed)");
        rb.ReturnDesc("newgen", "generation of the returned list");
        //-------------------------------------------------------------------
    }
    _orb.Listen(0);
    _port = _orb.GetListenPort();
    _orb.Start();
    _regAPI.registerName(service);
}

OOSServer::~OOSServer()
{
    _orb.ShutDown(true);
}

int
OOSServer::port() const
{
    return _port;
}

void
OOSServer::rpc_poll(FRT_RPCRequest *req)
{
    vespalib::LockGuard guard(_lock);
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(_state.size());
    for (uint32_t i = 0; i < _state.size(); ++i) {
        dst.SetString(&names[i], _state[i].c_str());
    }
    dst.AddInt32(_genCnt);
}

void
OOSServer::setState(const OOSState &state)
{
    std::vector<string> newState;
    for (OOSState::ITR itr = state.begin();
         itr != state.end(); ++itr)
    {
        if (itr->second) {
            newState.push_back(itr->first);
        }
    }
    vespalib::LockGuard guard(_lock);
    _state = newState;
    ++_genCnt;
    if (_genCnt == 0) {
        ++_genCnt;
    }
}

} // namespace mbus
