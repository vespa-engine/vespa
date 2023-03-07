// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "outward-check.h"
#include <vespa/log/log.h>

LOG_SETUP(".sentinel.outward-check");

namespace config::sentinel {

OutwardCheckContext::~OutwardCheckContext() = default;

OutwardCheck::OutwardCheck(const std::string &spec, OutwardCheckContext &context, int ping_timeout_ms)
  : _spec(spec),
    _context(context)
{
    _target = context.orb.GetTarget(spec.c_str());
    _req = context.orb.AllocRPCRequest();
    _req->SetMethodName("sentinel.check.connectivity");
    _req->GetParams()->AddString(context.targetHostname.c_str());
    _req->GetParams()->AddInt32(context.targetPortnum);
    _req->GetParams()->AddInt32(ping_timeout_ms);
    double ping_s = ping_timeout_ms * 0.001;
    double outer_timeout = 1.0 + (2 * ping_s);
    _target->InvokeAsync(_req, outer_timeout, this);
}

OutwardCheck::~OutwardCheck() = default;

void OutwardCheck::RequestDone(FRT_RPCRequest *req) {
    LOG_ASSERT(req == _req);
    if (req->CheckReturnTypes("s")) {
        std::string answer = _req->GetReturn()->GetValue(0)._string._str;
        if (answer == "ok") {
            LOG(debug, "ping to %s with reverse connectivity OK", _spec.c_str());
            _result = CcResult::ALL_OK;
        } else if (answer == "bad") {
            LOG(debug, "connected to %s, but reverse connectivity fails: %s",
                _spec.c_str(), answer.c_str());
            _result = CcResult::INDIRECT_PING_FAIL;
        } else {
            LOG(warning, "connected to %s, but strange reverse connectivity: %s",
                _spec.c_str(), answer.c_str());
            _result = CcResult::INDIRECT_PING_UNAVAIL;
        }
    } else if (req->GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD ||
               req->GetErrorCode() == FRTE_RPC_WRONG_PARAMS ||
               req->GetErrorCode() == FRTE_RPC_WRONG_RETURN)
    {
        LOG(debug, "Connected OK to %s but no reverse connectivity check available", _spec.c_str());
        _result = CcResult::INDIRECT_PING_UNAVAIL;
    } else {
        LOG(debug, "error on request to %s : %s (%d)", _spec.c_str(),
            req->GetErrorMessage(), req->GetErrorCode());
        _result = CcResult::CONN_FAIL;
    }
    _req->internal_subref();
    _req = nullptr;
    _target->internal_subref();
    _target = nullptr;
    _context.latch.countDown();
}

void OutwardCheck::classifyResult(CcResult value) {
    LOG_ASSERT(_result == CcResult::CONN_FAIL);
    _result = value;
}

}
