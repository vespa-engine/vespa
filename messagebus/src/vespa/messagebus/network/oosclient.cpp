// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "oosclient.h"
#include <vespa/fnet/frt/supervisor.h>

namespace mbus {

void
OOSClient::handleReply()
{
    if (!_req->CheckReturnTypes("Si")) {
        _target->SubRef();
        _target = 0;
        Schedule(1.0);
        return;
    }
    FRT_Values &ret = *(_req->GetReturn());
    uint32_t retGen = ret[1]._intval32;
    if (_reqGen != retGen) {
        StringList oos;
        uint32_t numNames = ret[0]._string_array._len;
        FRT_StringValue *names = ret[0]._string_array._pt;
        for (uint32_t idx = 0; idx < numNames; ++idx) {
            oos.push_back(string(names[idx]._str));
        }
        _oosList.swap(oos);
        _reqGen = retGen;
        _listGen = retGen;
    }
    Schedule(0.1);
}

void
OOSClient::handleConnect()
{
    if (_target == 0) {
        _target = _orb.GetTarget(_spec.c_str());
        _reqGen = 0;
    }
}

void
OOSClient::handleInvoke()
{
    assert(_target != 0);
    _req = _orb.AllocRPCRequest(_req);
    _req->SetMethodName("fleet.getOOSList");
    _req->GetParams()->AddInt32(_reqGen); // gencnt
    _req->GetParams()->AddInt32(60000);   // mstimeout
    _target->InvokeAsync(_req, 70.0, this);
}

void
OOSClient::PerformTask()
{
    if (_reqDone) {
        _reqDone = false;
        handleReply();
        return;
    }
    handleConnect();
    handleInvoke();
}

void
OOSClient::RequestDone(FRT_RPCRequest *req)
{
    assert(req == _req && !_reqDone);
    (void) req;
    _reqDone = true;
    ScheduleNow();
}

OOSClient::OOSClient(FRT_Supervisor &orb,
                     const string &mySpec)
    : FNET_Task(orb.GetScheduler()),
      _orb(orb),
      _spec(mySpec),
      _oosList(),
      _reqGen(0),
      _listGen(0),
      _dumpGen(0),
      _reqDone(false),
      _target(0),
      _req(0)
{
    ScheduleNow();
}

OOSClient::~OOSClient()
{
    Kill();
    if (_req != 0) {
        _req->Abort();
        _req->SubRef();
    }
    if (_target != 0) {
        _target->SubRef();
    }
}

void
OOSClient::dumpState(StringSet &dst)
{
    dst.insert(_oosList.begin(), _oosList.end());
    _dumpGen = _listGen;
}

} // namespace mbus
