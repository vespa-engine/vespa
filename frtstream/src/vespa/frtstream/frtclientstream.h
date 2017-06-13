// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "frtstream.h"
#include <vespa/fnet/frt/supervisor.h>

namespace frtstream {

class FrtClientStream : public FrtStream {
    FRT_Supervisor supervisor;
    FRT_RPCRequest* request;
    const double timeout;

    FRT_Target* target;
    bool executed;
    uint32_t _nextOutValue;

    FRT_Values& in() override;
    FRT_Value& nextOut() override;
public:
    FrtClientStream(const std::string& connectionSpec);
    ~FrtClientStream();

    using FrtStream::operator<<;
    using FrtStream::operator>>;
    FrtClientStream& operator<<(const Method& m);
};

} //end namespace frtstream

