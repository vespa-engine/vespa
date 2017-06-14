// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "frtconfigresponse.h"
#include <vespa/fnet/frt/frt.h>

namespace config {

FRTConfigResponse::FRTConfigResponse(FRT_RPCRequest * request)
    : _request(request),
      _responseState(EMPTY),
      _returnValues(_request->GetReturn())
{
    _request->AddRef();
}

FRTConfigResponse::~FRTConfigResponse()
{
    _request->SubRef();
}

bool
FRTConfigResponse::validateResponse()
{
    if (_request->IsError())
        _responseState = ERROR;
    if (_request->GetReturn()->GetNumValues() == 0)
        _responseState = EMPTY;
    if (_request->CheckReturnTypes(getResponseTypes().c_str())) {
        _returnValues = _request->GetReturn();
        _responseState = OK;
    }
    return (_responseState == OK);
}

bool
FRTConfigResponse::hasValidResponse() const
{
    return (_responseState == OK);
}

vespalib::string FRTConfigResponse::errorMessage() const { return _request->GetErrorMessage(); }
int FRTConfigResponse::errorCode() const { return _request->GetErrorCode(); }
bool FRTConfigResponse::isError() const { return _request->IsError(); }

//
// V1 Implementation
//
const vespalib::string FRTConfigResponseV1::RESPONSE_TYPES = "sssssilSs";

FRTConfigResponseV1::FRTConfigResponseV1(FRT_RPCRequest * request)
    : FRTConfigResponse(request),
      _key(),
      _value()
{
}

FRTConfigResponseV1::~FRTConfigResponseV1() {}

const vespalib::string &
FRTConfigResponseV1::getResponseTypes() const
{
    return RESPONSE_TYPES;
}

void
FRTConfigResponseV1::fill()
{
    const std::vector<vespalib::string> payload(getPayLoad());
    _value = ConfigValue(payload, calculateContentMd5(payload));
    _key = readKey();
    _state = ConfigState(vespalib::string((*_returnValues)[4]._string._str), (*_returnValues)[6]._intval64);
}

const ConfigKey
FRTConfigResponseV1::readKey() const
{
    return ConfigKey((*_returnValues)[3]._string._str, (*_returnValues)[0]._string._str, (*_returnValues)[8]._string._str, (*_returnValues)[2]._string._str);
}

const std::vector<vespalib::string>
FRTConfigResponseV1::getPayLoad() const
{
    uint32_t numStrings = (*_returnValues)[7]._string_array._len;
    FRT_StringValue *s = (*_returnValues)[7]._string_array._pt;
    std::vector<vespalib::string> payload;
    payload.reserve(numStrings);
    for (uint32_t i = 0; i < numStrings; i++) {
        payload.push_back(vespalib::string(s[i]._str));
    }
    return payload;
}

} // namespace config
