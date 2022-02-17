// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "connection.h"
#include <vespa/config/common/timingvalues.h>
#include <atomic>
#include <memory>

class FRT_Supervisor;
class FRT_Target;

namespace config {

class FRTConnection : public Connection {
private:
    const vespalib::string _address;
    FRT_Supervisor&       _supervisor;
    FRT_Target*           _target;
    vespalib::system_time _suspendedUntil;
    vespalib::system_time _suspendWarned;
    std::atomic<int>      _transientFailures;
    std::atomic<int>      _fatalFailures;
    duration              _transientDelay;
    duration              _fatalDelay;

    FRT_Target * getTarget();

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
    vespalib::system_time getSuspendedUntil() { return _suspendedUntil; }
    void setError(int errorCode) override;
    void setSuccess();
    void calculateSuspension(ErrorType type);
    duration getTransientDelay() { return _transientDelay; }
    duration getMaxTransientDelay() { return getTransientDelay() * 6; }
    void setTransientDelay(duration delay) override { _transientDelay = delay; }
    duration getFatalDelay() { return _fatalDelay; }
    duration getMaxFatalDelay() { return getFatalDelay() * 6; }
};

} // namespace config

