// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigagent.h"
#include "frtconfigrequestv3.h"
#include <vespa/config/common/trace.h>
#include <vespa/config/common/configresponse.h>
#include <vespa/config/common/iconfigholder.h>

#include <vespa/log/log.h>
LOG_SETUP(".config.frt.frtconfigagent");

namespace config {

FRTConfigAgent::FRTConfigAgent(std::shared_ptr<IConfigHolder> holder, const TimingValues & timingValues)
    : _holder(std::move(holder)),
      _timingValues(timingValues),
      _configState(),
      _latest(),
      _waitTime(0),
      _numConfigured(0),
      _failedRequests(0),
      _nextTimeout(_timingValues.initialTimeout)
{
}

FRTConfigAgent::~FRTConfigAgent() = default;

void
FRTConfigAgent::handleResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response)
{
    if (LOG_WOULD_LOG(spam)) {
        const ConfigKey & key(request.getKey());
        LOG(spam, "current state for %s: generation %" PRId64 " xxhash64 %s", key.toString().c_str(), _configState.generation, _configState.xxhash64.c_str());
    }
    if (response->validateResponse() && !response->isError()) {
        handleOKResponse(request, std::move(response));
    } else {
        handleErrorResponse(request, std::move(response));
    }
}

void
FRTConfigAgent::handleOKResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response)
{
    _failedRequests = 0;
    response->fill();
    if (LOG_WOULD_LOG(spam)) {
        LOG(spam, "trace(%s)", response->getTrace().toString().c_str());
    }

    ConfigState newState = response->getConfigState();
    if ( ! request.verifyState(newState)) {
        handleUpdatedGeneration(response->getKey(), newState, response->getValue());
    }
    setWaitTime(_timingValues.successDelay, 1);
    _nextTimeout = _timingValues.successTimeout;
}

void
FRTConfigAgent::handleUpdatedGeneration(const ConfigKey & key, const ConfigState & newState, const ConfigValue & configValue)
{
    if (LOG_WOULD_LOG(spam)) {
        LOG(spam, "new generation %" PRId64 " xxhash64:%s for key %s", newState.generation, newState.xxhash64.c_str(), key.toString().c_str());
        LOG(spam, "Old config: xxhash64:%s \n%s", _latest.getXxhash64().c_str(), _latest.asJson().c_str());
        LOG(spam, "New config: xxhash64:%s \n%s", configValue.getXxhash64().c_str(), configValue.asJson().c_str());
    }
    bool changed = false;
    if (_latest.getXxhash64() != configValue.getXxhash64()) {
        _latest = configValue;
        changed = true;
    }
    _configState = newState;

    if (LOG_WOULD_LOG(spam)) {
        LOG(spam, "updating holder for key %s,", key.toString().c_str());
    }
    _holder->handle(std::make_unique<ConfigUpdate>(_latest, changed, newState.generation));
    _numConfigured++;
}

using vespalib::to_s;

void
FRTConfigAgent::handleErrorResponse(const ConfigRequest & request, std::unique_ptr<ConfigResponse> response)
{
    _failedRequests++;
    int multiplier = std::min(_failedRequests, _timingValues.maxDelayMultiplier);
    setWaitTime(_numConfigured > 0 ? _timingValues.configuredErrorDelay : _timingValues.unconfiguredDelay, multiplier);
    _nextTimeout = _timingValues.errorTimeout;
    const ConfigKey & key(request.getKey());
    LOG(info, "Error response or no response from config server (key: %s) (errcode=%d, validresponse:%d), trying again in %f seconds",
        key.toString().c_str(), response->errorCode(), response->hasValidResponse() ? 1 : 0, to_s(_waitTime));
}

void
FRTConfigAgent::setWaitTime(duration delay, int multiplier)
{
    duration prevWait = _waitTime;
    _waitTime = _timingValues.fixedDelay + (multiplier * delay);
    LOG(spam, "Adjusting waittime from %f to %f", to_s(prevWait), to_s(_waitTime));
}

vespalib::duration FRTConfigAgent::getTimeout() const { return _nextTimeout; }
vespalib::duration FRTConfigAgent::getWaitTime() const { return _waitTime; }
const ConfigState & FRTConfigAgent::getConfigState() const { return _configState; }

}
