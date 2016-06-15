// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configresponse.h>
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/configstate.h>

class FRT_RPCRequest;
class FRT_Values;

namespace config {

/**
 * Baseclass for config responses.
 */
class FRTConfigResponse : public ConfigResponse {
private:
    FRTConfigResponse& operator=(const FRTConfigResponse&);
public:
    typedef std::unique_ptr<FRTConfigResponse> UP;
    FRTConfigResponse(FRT_RPCRequest * request);
    virtual ~FRTConfigResponse();

    bool validateResponse();
    bool hasValidResponse() const;
    vespalib::string errorMessage() const;
    int errorCode() const;
    bool isError() const;
    virtual const vespalib::string & getResponseTypes() const = 0;

private:
    enum ResponseState { EMPTY, OK, ERROR };

    FRT_RPCRequest * _request;
    ResponseState _responseState;
protected:
    FRT_Values * _returnValues;
};

class FRTConfigResponseV1 : public FRTConfigResponse {
private:
    FRTConfigResponseV1& operator=(const FRTConfigResponseV1&);
public:
    FRTConfigResponseV1(FRT_RPCRequest * request);

    const ConfigKey & getKey() const { return _key; }
    const ConfigValue & getValue() const { return _value; }
    const Trace & getTrace() const { return _trace; }

    const ConfigState & getConfigState() const { return _state; }

    void fill();

private:
    static const vespalib::string RESPONSE_TYPES;

    const std::vector<vespalib::string> getPayLoad() const;
    const ConfigKey readKey() const;
    const vespalib::string & getResponseTypes() const;

    ConfigKey _key;
    ConfigValue _value;
    ConfigState _state;
    Trace _trace;
};

} // namespace config

