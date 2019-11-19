// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "dynamicthrottlepolicy.h"
#include "steadytimer.h"
#include <climits>

#include <vespa/log/log.h>
LOG_SETUP(".dynamicthrottlepolicy");

namespace mbus {

DynamicThrottlePolicy::DynamicThrottlePolicy() :
    _timer(new SteadyTimer()),
    _numSent(0),
    _numOk(0),
    _resizeRate(3),
    _resizeTime(_timer->getMilliTime()),
    _timeOfLastMessage(_timer->getMilliTime()),
    _idleTimePeriod(60000),
    _efficiencyThreshold(1),
    _windowSizeIncrement(20),
    _windowSize(_windowSizeIncrement),
    _maxWindowSize(INT_MAX),
    _minWindowSize(_windowSizeIncrement),
    _windowSizeBackOff(0.9),
    _weight(1),
    _localMaxThroughput(0)
{ }

DynamicThrottlePolicy::DynamicThrottlePolicy(double windowSizeIncrement) :
    _timer(new SteadyTimer()),
    _numSent(0),
    _numOk(0),
    _resizeRate(3),
    _resizeTime(_timer->getMilliTime()),
    _timeOfLastMessage(_timer->getMilliTime()),
    _idleTimePeriod(60000),
    _efficiencyThreshold(1),
    _windowSizeIncrement(windowSizeIncrement),
    _windowSize(_windowSizeIncrement),
    _maxWindowSize(INT_MAX),
    _minWindowSize(_windowSizeIncrement),
    _windowSizeBackOff(0.9),
    _weight(1),
    _localMaxThroughput(0)
{ }

DynamicThrottlePolicy::DynamicThrottlePolicy(ITimer::UP timer) :
    _timer(std::move(timer)),
    _numSent(0),
    _numOk(0),
    _resizeRate(3),
    _resizeTime(_timer->getMilliTime()),
    _timeOfLastMessage(_timer->getMilliTime()),
    _idleTimePeriod(60000),
    _efficiencyThreshold(1),
    _windowSizeIncrement(20),
    _windowSize(_windowSizeIncrement),
    _maxWindowSize(INT_MAX),
    _minWindowSize(_windowSizeIncrement),
    _windowSizeBackOff(0.9),
    _weight(1),
    _localMaxThroughput(0)
{ }

DynamicThrottlePolicy &
DynamicThrottlePolicy::setEfficiencyThreshold(double efficiencyThreshold)
{
    _efficiencyThreshold = efficiencyThreshold;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setWindowSizeIncrement(double windowSizeIncrement)
{
    _windowSizeIncrement = windowSizeIncrement;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setWindowSizeBackOff(double windowSizeBackOff)
{
    _windowSizeBackOff = windowSizeBackOff;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setResizeRate(uint32_t resizeRate)
{
    _resizeRate = resizeRate;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setWeight(double weight)
{
    _weight = weight;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setIdleTimePeriod(uint64_t period)
{
    _idleTimePeriod = period;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setMaxWindowSize(double max)
{
    _maxWindowSize = max;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setMinWindowSize(double min)
{
    _minWindowSize = min;
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setMaxPendingCount(uint32_t maxCount)
{
    StaticThrottlePolicy::setMaxPendingCount(maxCount);
    _maxWindowSize = maxCount;
    return *this;
}

bool
DynamicThrottlePolicy::canSend(const Message &msg, uint32_t pendingCount)
{
    if (!StaticThrottlePolicy::canSend(msg, pendingCount)) {
        return false;
    }
    uint64_t time = _timer->getMilliTime();
    if (time - _timeOfLastMessage > _idleTimePeriod) {
        _windowSize = std::min(_windowSize, (double) pendingCount + _windowSizeIncrement);
    }
    _timeOfLastMessage = time;
    return pendingCount < _windowSize;
}

void
DynamicThrottlePolicy::processMessage(Message &msg)
{
    StaticThrottlePolicy::processMessage(msg);
    if (++_numSent < _windowSize * _resizeRate) {
        return;
    }

    uint64_t time = _timer->getMilliTime();
    double elapsed = time - _resizeTime;
    _resizeTime = time;

    double throughput = _numOk / elapsed;
    _numSent = 0;
    _numOk = 0;

    if (throughput > _localMaxThroughput * 1.01) {
        LOG(debug, "WindowSize = %.2f, Throughput = %f", _windowSize, throughput);
        _localMaxThroughput = throughput;
        _windowSize += _weight*_windowSizeIncrement;
    } else {
        // scale up/down throughput for comparing to window size
        double period = 1;
        while(throughput*period/_windowSize < 2) {
            period *= 10;
        }
        while(throughput*period/_windowSize > 2) {
            period *= 0.1;
        }
        double efficiency = throughput*period/_windowSize;
        LOG(debug, "WindowSize = %.2f, Throughput = %f, Efficiency = %.2f, Elapsed = %.2f, Period = %.2f", _windowSize, throughput, efficiency, elapsed, period);

        if (efficiency < _efficiencyThreshold) {
            double newSize = std::min(throughput * period, _windowSize);
            _windowSize = std::min(newSize * _windowSizeBackOff, _windowSize - 2 * _windowSizeIncrement);
            _localMaxThroughput = 0;
        } else {
            _windowSize += _weight*_windowSizeIncrement;
        }
    }
    _windowSize = std::max(_minWindowSize, _windowSize);
    _windowSize = std::min(_maxWindowSize, _windowSize);
}

void
DynamicThrottlePolicy::processReply(Reply &reply)
{
    StaticThrottlePolicy::processReply(reply);
    if (!reply.hasErrors()) {
        ++_numOk;
    }
}

} // namespace mbus
