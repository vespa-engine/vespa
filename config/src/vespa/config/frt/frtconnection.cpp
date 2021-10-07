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
      _suspendedUntil(0),
      _suspendWarned(0),
      _transientFailures(0),
      _fatalFailures(0),
      _transientDelay(timingValues.transientDelay),
      _fatalDelay(timingValues.fatalDelay)
{
}

FRTConnection::~FRTConnection()
{
    if (_target != nullptr) {
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
FRTConnection::invoke(FRT_RPCRequest * req, double timeout, FRT_IRequestWait * waiter)
{
    getTarget()->InvokeAsync(req, timeout, waiter);
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
    _suspendedUntil = 0;
}

void FRTConnection::calculateSuspension(ErrorType type)
{
    int64_t delay = 0;
    switch(type) {
    case TRANSIENT:
        _transientFailures.fetch_add(1);
        delay = _transientFailures * getTransientDelay();
        if (delay > getMaxTransientDelay()) {
            delay = getMaxTransientDelay();
        }
        LOG(warning, "Connection to %s failed or timed out", _address.c_str());
        break;
    case FATAL:
        _fatalFailures.fetch_add(1);
        delay = _fatalFailures * getFatalDelay();
        if (delay > getMaxFatalDelay()) {
            delay = getMaxFatalDelay();
        }
        break;
    }
    int64_t now = milliSecsSinceEpoch();
    _suspendedUntil = now + delay;
    if (_suspendWarned < (now - 5000)) {
        char date[32];
        struct tm* timeinfo;
        time_t suspendedSeconds = _suspendedUntil / 1000;
        timeinfo = gmtime(&suspendedSeconds);
        strftime(date, 32, "%Y-%m-%d %H:%M:%S %Z", timeinfo);
        LOG(warning, "FRT Connection %s suspended until %s", _address.c_str(), date);
        _suspendWarned = now;
    }
}

FRT_RPCRequest *
FRTConnection::allocRPCRequest() {
    return _supervisor.AllocRPCRequest();
}

using namespace std::chrono;
int64_t
FRTConnection::milliSecsSinceEpoch() {
    return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

}
