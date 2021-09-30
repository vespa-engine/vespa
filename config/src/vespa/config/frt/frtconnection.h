// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    FRTConnection(const FRTConnection&);
    FRTConnection& operator=(const FRTConnection&);

    const vespalib::string _address;
    FRT_Supervisor& _supervisor;
    FRT_Target* _target;
    int64_t _suspendedUntil;
    int64_t _suspendWarned;
    std::atomic<int> _transientFailures;
    std::atomic<int> _fatalFailures;
    int64_t _transientDelay;
    int64_t _fatalDelay;

    FRT_Target * getTarget();

public:
    typedef std::shared_ptr<FRTConnection> SP;
    enum ErrorType { TRANSIENT, FATAL };

    FRTConnection(const vespalib::string & address, FRT_Supervisor & supervisor, const TimingValues & timingValues);
    ~FRTConnection() override;

    FRT_RPCRequest * allocRPCRequest() override;
    void invoke(FRT_RPCRequest * req, double timeout, FRT_IRequestWait * waiter) override;
    const vespalib::string & getAddress() const override { return _address; }
    int64_t getSuspendedUntil() { return _suspendedUntil; }
    void setError(int errorCode) override;
    void setSuccess();
    void calculateSuspension(ErrorType type);
    int64_t getTransientDelay() { return _transientDelay; }
    int64_t getMaxTransientDelay() { return getTransientDelay() * 6; }
    void setTransientDelay(int64_t delay) override { _transientDelay = delay; }
    int64_t getFatalDelay() { return _fatalDelay; }
    int64_t getMaxFatalDelay() { return getFatalDelay() * 6; }
    static int64_t milliSecsSinceEpoch();
};

} // namespace config

