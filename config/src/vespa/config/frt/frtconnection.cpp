// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconnection.h"
#include <vespa/config/common/errorcode.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP(".config.frt.frtconnection");

using namespace vespalib;

namespace config {

FRTConnection::FRTConnection(const vespalib::string& address, FRT_Supervisor& supervisor, const TimingValues & timingValues)
    : _address(address),
      _supervisor(supervisor),
      _target(0),
      _suspendedUntil(),
      _suspendWarned(),
      _transientFailures(0),
      _fatalFailures(0),
      _transientDelay(timingValues.transientDelay),
      _fatalDelay(timingValues.fatalDelay)
{
}

FRTConnection::~FRTConnection()
{
    if (_target != nullptr) {
        LOG(debug, "Shutting down %s", _address.c_str());
        _target->SubRef();
        _target = nullptr;
    }
}

FRT_Target *
FRTConnection::getTarget()
{
    if (_target == nullptr) {
        _target = _supervisor.GetTarget(_address.c_str());
    } else if ( ! _target->IsValid()) {
        _target->SubRef();
        _target = _supervisor.GetTarget(_address.c_str());
    }
    return _target;
}

void
FRTConnection::invoke(FRT_RPCRequest * req, duration timeout, FRT_IRequestWait * waiter)
{
    getTarget()->InvokeAsync(req, vespalib::to_s(timeout), waiter);
}

void
FRTConnection::setError(int errorCode)
{
    switch(errorCode) {
    case FRTE_RPC_CONNECTION:
    case FRTE_RPC_TIMEOUT:
        calculateSuspension(TRANSIENT); break;
    case ErrorCode::UNKNOWN_CONFIG:
    case ErrorCode::UNKNOWN_DEFINITION:
    case ErrorCode::UNKNOWN_VERSION:
    case ErrorCode::UNKNOWN_CONFIGID:
    case ErrorCode::UNKNOWN_DEF_MD5:
    case ErrorCode::ILLEGAL_NAME:
    case ErrorCode::ILLEGAL_VERSION:
    case ErrorCode::ILLEGAL_CONFIGID:
    case ErrorCode::ILLEGAL_DEF_MD5:
    case ErrorCode::ILLEGAL_CONFIG_MD5:
    case ErrorCode::ILLEGAL_TIMEOUT:
    case ErrorCode::OUTDATED_CONFIG:
    case ErrorCode::INTERNAL_ERROR:
        calculateSuspension(FATAL); break;
    }
}

void FRTConnection::setSuccess()
{
    _transientFailures = 0;
    _fatalFailures = 0;
    _suspendedUntil = system_time();
}

void FRTConnection::calculateSuspension(ErrorType type)
{
    duration delay = duration::zero();
    switch(type) {
    case TRANSIENT:
        _transientFailures.fetch_add(1);
        delay = _transientFailures.load(std::memory_order_relaxed) * getTransientDelay();
        if (delay > getMaxTransientDelay()) {
            delay = getMaxTransientDelay();
        }
        LOG(warning, "Connection to %s failed or timed out", _address.c_str());
        break;
    case FATAL:
        _fatalFailures.fetch_add(1);
        delay = _fatalFailures.load(std::memory_order_relaxed) * getFatalDelay();
        if (delay > getMaxFatalDelay()) {
            delay = getMaxFatalDelay();
        }
        break;
    }
    system_time now = system_clock::now();
    /*
     * On Darwin, the std::chrono::steady_clock period (std::nano) is
     * not exactly divisible by the std::chrono::system_clock period
     * (std::micro). Thus we need to use std::chrono::duration_cast to
     * convert from steady_time::duration to system_time::duration.
     */
    _suspendedUntil = now + std::chrono::duration_cast<system_time::duration>(delay);
    if (_suspendWarned < (now - 5s)) {
        LOG(warning, "FRT Connection %s suspended until %s", _address.c_str(), vespalib::to_string(_suspendedUntil).c_str());
        _suspendWarned = now;
    }
}

FRT_RPCRequest *
FRTConnection::allocRPCRequest() {
    return _supervisor.AllocRPCRequest();
}

}
