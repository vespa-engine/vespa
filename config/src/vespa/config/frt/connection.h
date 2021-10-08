// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
class FRT_RPCRequest;
class FRT_IRequestWait;

namespace config {

class Connection {
public:
    virtual FRT_RPCRequest * allocRPCRequest() = 0;
    virtual void setError(int errorCode) = 0;
    virtual void invoke(FRT_RPCRequest * req, double timeout, FRT_IRequestWait * waiter) = 0;
    virtual const vespalib::string & getAddress() const = 0;
    virtual void setTransientDelay(int64_t delay) = 0;
    virtual ~Connection() { }
};

}

