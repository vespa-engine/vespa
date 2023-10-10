// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "connection.h"
#include <vespa/config/common/timingvalues.h>
#include <memory>
#include <mutex>

class FRT_Supervisor;
class FRT_Target;

namespace config {

class FRTConnection : public Connection {
public:
    typedef std::shared_ptr<FRTConnection> SP;
    enum ErrorType { TRANSIENT, FATAL };

    FRTConnection(const vespalib::string & address, FRT_Supervisor & supervisor, const TimingValues & timingValues);
    FRTConnection(const FRTConnection&) = delete;
    FRTConnection& operator=(const FRTConnection&) = delete;
    ~FRTConnection() override;

    FRT_RPCRequest * allocRPCRequest() override;
    void invoke(FRT_RPCRequest * req, duration timeout, FRT_IRequestWait * waiter) override;
    const vespalib::string & getAddress() const override { return _address; }
    vespalib::steady_time getSuspendedUntil() const { return _suspendedUntil; }
    void setError(int errorCode) override;
    void setSuccess();
private:
    FRT_Target * getTarget();

    void calculateSuspension(ErrorType type);

    const vespalib::string _address;
    const duration         _transientDelay;
    const duration         _fatalDelay;
    FRT_Supervisor&        _supervisor;
    std::mutex             _lock;
    FRT_Target*            _target;
    vespalib::steady_time  _suspendedUntil;
    vespalib::steady_time  _suspendWarned;
    uint32_t               _transientFailures;
    uint32_t               _fatalFailures;
};

} // namespace config

