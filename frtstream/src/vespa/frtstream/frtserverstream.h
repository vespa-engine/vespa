// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/frtstream/frtstream.h>

namespace frtstream {

class FrtServerStream : public FrtStream {
    FRT_RPCRequest* request;
    uint32_t _nextOutValue;

    FRT_Values& in() override {
        return *request->GetReturn();
    }

    FRT_Value& nextOut() override {
        return request->GetParams()->GetValue(_nextOutValue++);
    }
public:
    FrtServerStream(FRT_RPCRequest* req) :
        request(req),
        _nextOutValue(0) {}

    using FrtStream::operator<<;
    using FrtStream::operator>>;
};

} //end namespace frtstream
