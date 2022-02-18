// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configrequest.h>
#include <vespa/config/common/configkey.h>

class FRT_Values;
class FRT_RPCRequest;

namespace config {

class ConfigResponse;
class Connection;

/**
 * Class representing a FRT config request.
 */
class FRTConfigRequest : public ConfigRequest {
public:
    FRTConfigRequest(Connection * connection, const ConfigKey & key);
    ~FRTConfigRequest();

    bool abort() override;
    void setError(int errorCode) override;
    const ConfigKey & getKey() const override;

    FRT_RPCRequest* getRequest() { return _request; }
    virtual std::unique_ptr<ConfigResponse> createResponse(FRT_RPCRequest * request) const = 0;
protected:
    FRT_RPCRequest * _request;
    FRT_Values     & _parameters;
private:
    Connection     * _connection;
    const ConfigKey  _key;
};

}

