// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configresponse.h>

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
    ~FRTConfigResponse();

    bool validateResponse() override;
    bool hasValidResponse() const override;
    vespalib::string errorMessage() const override;
    int errorCode() const override;
    bool isError() const override;
    virtual const vespalib::string & getResponseTypes() const = 0;

private:
    enum ResponseState { EMPTY, OK, ERROR };

    FRT_RPCRequest * _request;
    ResponseState _responseState;
protected:
    FRT_Values * _returnValues;
};

} // namespace config

