// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigrequest.h"
#include "frtconfigresponse.h"
#include "connection.h"
#include <vespa/fnet/frt/frt.h>
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configstate.h>

namespace config {

FRTConfigRequest::FRTConfigRequest(Connection * connection, const ConfigKey & key)
    : _request(connection->allocRPCRequest()),
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

bool
FRTConfigRequest::isAborted() const
{
    return (_request->GetErrorCode() == FRTE_RPC_ABORT);
}

const vespalib::string FRTConfigRequestV1::REQUEST_TYPES = "sssssllsSi";

FRTConfigRequestV1::FRTConfigRequestV1(const ConfigKey & key,
                                       Connection * connection,
                                       const vespalib::string & configMd5,
                                       int64_t generation,
                                       int64_t serverTimeout)
    : FRTConfigRequest(connection, key)
{
    _request->SetMethodName("config.v1.getConfig");
    _parameters.AddString(key.getDefName().c_str());
    _parameters.AddString("");
    _parameters.AddString(key.getDefMd5().c_str());
    _parameters.AddString(key.getConfigId().c_str());
    _parameters.AddString(configMd5.c_str());
    _parameters.AddInt64(generation);
    _parameters.AddInt64(serverTimeout);
    _parameters.AddString(key.getDefNamespace().c_str());
    const std::vector<vespalib::string> & schema(key.getDefSchema());
    FRT_StringValue * schemaValue = _parameters.AddStringArray(schema.size());
    for (size_t i = 0; i < schema.size(); i++) {
        _parameters.SetString(&schemaValue[i], schema[i].c_str());
    }
    _parameters.AddInt32(1);
}

bool
FRTConfigRequestV1::verifyKey(const ConfigKey & key) const
{
    return (key.getDefName().compare(_parameters[0]._string._str) == 0 &&
            key.getDefNamespace().compare(_parameters[7]._string._str) == 0 &&
            key.getConfigId().compare(_parameters[3]._string._str) == 0 &&
            key.getDefMd5().compare(_parameters[2]._string._str) == 0);
}

bool
FRTConfigRequestV1::verifyState(const ConfigState & state) const
{
    return (state.md5.compare(_parameters[4]._string._str) == 0 &&
            state.generation == static_cast<int64_t>(_parameters[5]._intval64));
}

ConfigResponse::UP
FRTConfigRequestV1::createResponse(FRT_RPCRequest * request) const
{
    return ConfigResponse::UP(new FRTConfigResponseV1(request));
}

} // namespace config
