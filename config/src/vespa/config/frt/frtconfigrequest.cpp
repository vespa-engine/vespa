// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigrequest.h"
#include "frtconfigresponse.h"
#include "connection.h"
#include <vespa/fnet/frt/rpcrequest.h>

namespace config {

FRTConfigRequest::FRTConfigRequest(Connection * connection, const ConfigKey & key)
    : ConfigRequest(),
      _request(connection->allocRPCRequest()),
      _parameters(*_request->GetParams()),
      _connection(connection),
      _key(key)
{
}

FRTConfigRequest::~FRTConfigRequest()
{
    _request->SubRef();
}

bool
FRTConfigRequest::abort()
{
    return _request->Abort();
}

void
FRTConfigRequest::setError(int errorCode)
{
    _connection->setError(errorCode);
}

const ConfigKey &
FRTConfigRequest::getKey() const
{
    return _key;
}

} // namespace config
