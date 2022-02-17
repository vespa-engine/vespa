// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    _resizeRate(3.0),
    _resizeTime(0),
    _timeOfLastMessage(_timer->getMilliTime()),
    _idleTimePeriod(60000),
    _efficiencyThreshold(1),
    _windowSizeIncrement(20),
    _windowSize(_windowSizeIncrement),
    _maxWindowSize(INT_MAX),
    _minWindowSize(_windowSizeIncrement),
    _decrementFactor(2.0),
    _windowSizeBackOff(0.9),
    _weight(1),
    _localMaxThroughput(0)
{ }

DynamicThrottlePolicy::DynamicThrottlePolicy(double windowSizeIncrement) :
    _timer(new SteadyTimer()),
    _numSent(0),
    _numOk(0),
    _resizeRate(3.0),
    _resizeTime(0),
    _timeOfLastMessage(_timer->getMilliTime()),
    _idleTimePeriod(60000),
    _efficiencyThreshold(1),
    _windowSizeIncrement(windowSizeIncrement),
    _windowSize(_windowSizeIncrement),
    _maxWindowSize(INT_MAX),
    _minWindowSize(_windowSizeIncrement),
    _decrementFactor(2.0),
    _windowSizeBackOff(0.9),
    _weight(1),
    _localMaxThroughput(0)
{ }

DynamicThrottlePolicy::DynamicThrottlePolicy(ITimer::UP timer) :
    _timer(std::move(timer)),
    _numSent(0),
    _numOk(0),
    _resizeRate(3.0),
    _resizeTime(0),
    _timeOfLastMessage(_timer->getMilliTime()),
    _idleTimePeriod(60000),
    _efficiencyThreshold(1),
    _windowSizeIncrement(20),
    _windowSize(_windowSizeIncrement),
    _maxWindowSize(INT_MAX),
    _minWindowSize(_windowSizeIncrement),
    _decrementFactor(2.0),
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
    _windowSize = std::max(_windowSize, _windowSizeIncrement);
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setWindowSizeBackOff(double windowSizeBackOff)
{
    _windowSizeBackOff = std::max(0.0, std::min(1.0, windowSizeBackOff));
    return *this;
}

DynamicThrottlePolicy &
DynamicThrottlePolicy::setResizeRate(double resizeRate)
{
    _resizeRate = std::max(2.0, resizeRate);
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
    _windowSize = std::max(_minWindowSize, _windowSizeIncrement);
    return *this;
}

DynamicThrottlePolicy&
DynamicThrottlePolicy::setWindowSizeDecrementFactor(double decrementFactor)
{
    _decrementFactor = decrementFactor;
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
        _windowSize = std::max(_minWindowSize, std::min(_windowSize, pendingCount + _windowSizeIncrement));
        LOG(debug, "Idle time exceeded; WindowSize = %.2f", _windowSize);
    }
    _timeOfLastMessage = time;
    auto windowSizeFloored = static_cast<uint32_t>(_windowSize);
    // Use floating point window sizes, so the algorithm sees the difference between 1.1 and 1.9 window size.
    bool carry = _numSent < ((_windowSize * _resizeRate) * (_windowSize - windowSizeFloored));
    return pendingCount < (windowSizeFloored + (carry ? 1 : 0));
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

    if (throughput > _localMaxThroughput) {
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

        if (efficiency < _efficiencyThreshold) {
            _windowSize = std::min(_windowSize * _windowSizeBackOff, _windowSize - _decrementFactor * _windowSizeIncrement);
            _localMaxThroughput = 0;
        } else {
            _windowSize += _weight*_windowSizeIncrement;
        }
        LOG(debug, "WindowSize = %.2f, Throughput = %f, Efficiency = %.2f, Elapsed = %.2f, Period = %.2f",
            _windowSize, throughput, efficiency, elapsed, period);
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
