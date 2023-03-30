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
      _transientDelay(timingValues.transientDelay),
      _fatalDelay(timingValues.fatalDelay),
      _supervisor(supervisor),
      _lock(),
      _target(0),
      _suspendedUntil(),
      _suspendWarned(),
      _transientFailures(0),
      _fatalFailures(0)
{
}

FRTConnection::~FRTConnection()
{
    if (_target != nullptr) {
        LOG(debug, "Shutting down %s", _address.c_str());
        _target->internal_subref();
        _target = nullptr;
    }
}

FRT_Target *
FRTConnection::getTarget()
{
    std::lock_guard guard(_lock);
    if (_target == nullptr) {
        _target = _supervisor.GetTarget(_address.c_str());
    } else if ( ! _target->IsValid()) {
        _target->internal_subref();
        _target = _supervisor.GetTarget(_address.c_str());
    }
    _target->internal_addref();
    return _target;
}

void
FRTConnection::invoke(FRT_RPCRequest * req, duration timeout, FRT_IRequestWait * waiter)
{
    FRT_Target * target = getTarget();
    target->InvokeAsync(req, vespalib::to_s(timeout), waiter);
    target->internal_subref();
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
    std::lock_guard guard(_lock);
    _transientFailures = 0;
    _fatalFailures = 0;
    _suspendedUntil = steady_time();
}

namespace {

constexpr uint32_t MAX_DELAY_MULTIPLIER = 6u;
constexpr vespalib::duration WARN_INTERVAL = 10s;

}

void FRTConnection::calculateSuspension(ErrorType type)
{
    duration delay = duration::zero();
    steady_time now = steady_clock::now();
    std::lock_guard guard(_lock);
    switch(type) {
    case TRANSIENT:
        delay = std::min(MAX_DELAY_MULTIPLIER, ++_transientFailures) * _transientDelay;
        LOG(debug, "Connection to %s failed or timed out", _address.c_str());
        break;
    case FATAL:
        delay = std::min(MAX_DELAY_MULTIPLIER, ++_fatalFailures) * _fatalDelay;
        break;
    }
    _suspendedUntil = now + delay;
    if (_suspendWarned < (now - WARN_INTERVAL)) {
        LOG(debug, "FRT Connection %s suspended until %s", _address.c_str(), vespalib::to_string(to_utc(_suspendedUntil)).c_str());
        _suspendWarned = now;
    }
}

FRT_RPCRequest *
FRTConnection::allocRPCRequest() {
    return _supervisor.AllocRPCRequest();
}

}
