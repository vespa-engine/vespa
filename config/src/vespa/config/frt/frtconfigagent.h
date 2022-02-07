// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configstate.h>
#include <vespa/config/common/timingvalues.h>
#include <vespa/config/common/configresponse.h>
#include <vespa/config/common/configrequest.h>
#include <vespa/config/common/configvalue.h>

namespace config {

class IConfigHolder;

class ConfigAgent
{
public:
    typedef std::unique_ptr<ConfigAgent> UP;
    virtual void handleResponse(const ConfigRequest & request, ConfigResponse::UP response) = 0;

    virtual uint64_t getTimeout() const = 0;
    virtual uint64_t getWaitTime() const = 0;
    virtual const ConfigState & getConfigState() const = 0;

    virtual ~ConfigAgent() = default;
};

class FRTConfigAgent : public ConfigAgent
{
public:
    FRTConfigAgent(std::shared_ptr<IConfigHolder> holder, const TimingValues & timingValues);
    ~FRTConfigAgent() override;
    void handleResponse(const ConfigRequest & request, ConfigResponse::UP response) override;
    uint64_t getTimeout() const override;
    uint64_t getWaitTime() const override;
    const ConfigState & getConfigState() const override;
private:
    void handleUpdatedGeneration(const ConfigKey & key, const ConfigState & newState, const ConfigValue & configValue);
    void handleOKResponse(const ConfigRequest & request, ConfigResponse::UP response);
    void handleErrorResponse(const ConfigRequest & request, ConfigResponse::UP response);
    void setWaitTime(uint64_t delay, int multiplier);

    std::shared_ptr<IConfigHolder> _holder;
    const TimingValues _timingValues;
    ConfigState        _configState;
    ConfigValue        _latest;
    uint64_t           _waitTime;
    uint64_t           _numConfigured;
    unsigned int       _failedRequests;
    uint64_t           _nextTimeout;
};

}

