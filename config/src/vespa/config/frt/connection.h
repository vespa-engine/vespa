// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <string>

class FRT_RPCRequest;
class FRT_IRequestWait;

namespace config {

class Connection {
public:
    using duration = vespalib::duration;
    virtual FRT_RPCRequest * allocRPCRequest() = 0;
    virtual void setError(int errorCode) = 0;
    virtual void invoke(FRT_RPCRequest * req, duration timeout, FRT_IRequestWait * waiter) = 0;
    virtual const std::string & getAddress() const = 0;
    virtual ~Connection() = default;
};

}

