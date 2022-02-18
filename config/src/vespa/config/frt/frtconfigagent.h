// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configstate.h>
#include <vespa/config/common/timingvalues.h>
#include <vespa/config/common/configvalue.h>

namespace config {

class IConfigHolder;
class ConfigResponse;
class ConfigRequest;
class ConfigKey;

class ConfigAgent
{
public:
    using UP = std::unique_ptr<ConfigAgent>;
    using duration = vespalib::duration;
    virtual void handleResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response) = 0;

    virtual duration getTimeout() const = 0;
    virtual duration getWaitTime() const = 0;
    virtual const ConfigState & getConfigState() const = 0;

    virtual ~ConfigAgent() = default;
};

class FRTConfigAgent : public ConfigAgent
{
public:
    FRTConfigAgent(std::shared_ptr<IConfigHolder> holder, const TimingValues & timingValues);
    ~FRTConfigAgent() override;
    void handleResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response) override;
    duration getTimeout() const override;
    duration getWaitTime() const override;
    const ConfigState & getConfigState() const override;
private:
    void handleUpdatedGeneration(const ConfigKey & key, const ConfigState & newState, const ConfigValue & configValue);
    void handleOKResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response);
    void handleErrorResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response);
    void setWaitTime(duration delay, int multiplier);

    std::shared_ptr<IConfigHolder> _holder;
    const TimingValues _timingValues;
    ConfigState        _configState;
    ConfigValue        _latest;
    duration           _waitTime;
    uint64_t           _numConfigured;
    unsigned int       _failedRequests;
    duration           _nextTimeout;
};

}

