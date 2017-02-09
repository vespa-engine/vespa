// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "frtclientstream.h"

using namespace fnet;

namespace frtstream {

FrtClientStream::FrtClientStream(const std::string& connectionSpec)
    : timeout(30),
      executed(false),
      _nextOutValue(0)

{
    supervisor.Start();
    target = supervisor.GetTarget(connectionSpec.c_str());
    if( ! target ) {
        supervisor.ShutDown(true);
        throw ConnectionException();
    }
    request = supervisor.AllocRPCRequest();
}

FrtClientStream::~FrtClientStream() {
    request->SubRef();
    target->SubRef();
    supervisor.ShutDown(true);
}

FrtClientStream& FrtClientStream::operator<<(const Method& m) {
    executed = false;
    request = supervisor.AllocRPCRequest(request);
    request->SetMethodName(m.name().c_str());

    return *this;
}

FRT_Values& FrtClientStream::in() {
    return *request->GetParams();
}
FRT_Value& FrtClientStream::nextOut() {
    if(! executed ) {
        target->InvokeSync(request, timeout);
        executed = true;
        _nextOutValue = 0;
        if( request->GetErrorCode() != FRTE_NO_ERROR ) {
            throw InvokationException(request->GetErrorCode(),
                                      request->GetErrorMessage());
        }
    }
    return request->GetReturn()->GetValue(_nextOutValue++);
}


} //end namespace frtstream
