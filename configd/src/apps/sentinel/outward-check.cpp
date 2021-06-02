// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "outward-check.h"
#include <vespa/log/log.h>

LOG_SETUP(".outward-check");

namespace config::sentinel {

OutwardCheck::OutwardCheck(const std::string &spec, const char * myHostname, int myPortnum,
                           FRT_Supervisor &orb, vespalib::CountDownLatch &latch)
  : _spec(spec),
    _countDownLatch(latch)
{
    _target = orb.GetTarget(spec.c_str());
    _req = orb.AllocRPCRequest();
    _req->SetMethodName("sentinel.check.connectivity");
    _req->GetParams()->AddString(myHostname);
    _req->GetParams()->AddInt32(myPortnum);
    _req->GetParams()->AddInt32(500);
    _target->InvokeAsync(_req, 1.500, this);
}

OutwardCheck::~OutwardCheck() = default;

void OutwardCheck::RequestDone(FRT_RPCRequest *req) {
    LOG_ASSERT(req == _req);
    if (req->CheckReturnTypes("s")) {
        std::string answer = _req->GetReturn()->GetValue(0)._string._str;
        if (answer == "ok") {
            LOG(info, "ping to %s with reverse connectivity OK", _spec.c_str());
            _wasOk = true;
        } else {
            LOG(warning, "connected to %s, but reverse connectivity fails: %s",
                _spec.c_str(), answer.c_str());
            _wasBad = true;
        }
    } else if (req->GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD ||
               req->GetErrorCode() == FRTE_RPC_WRONG_PARAMS)
    {
        _wasOk = true;
        LOG(info, "Connected OK to %s but no reverse connectivity check available", _spec.c_str());
    } else {
        _wasBad = true;
        LOG(warning, "error on ping to %s : %s (%d)", _spec.c_str(),
            req->GetErrorMessage(), req->GetErrorCode());
    }
    _req->SubRef();
    _req = nullptr;
    _target->SubRef();
    _target = nullptr;
    _countDownLatch.countDown();
}

}
