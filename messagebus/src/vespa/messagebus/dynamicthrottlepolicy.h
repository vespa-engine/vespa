// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "itimer.h"
#include "staticthrottlepolicy.h"

namespace mbus {

/**
 * This is an implementatin of the {@link ThrottlePolicy} that offers dynamic limits to the number of pending
 * messages a {@link SourceSession} is allowed to have.
 *
 * <b>NOTE:</b> By context, "pending" is refering to the number of sent messages that have not been replied to
 * yet.
 */
class DynamicThrottlePolicy: public StaticThrottlePolicy {
public:
private:
    ITimer::UP  _timer;
    uint32_t    _numSent;
    uint32_t    _numOk;
    double      _resizeRate;
    uint64_t    _resizeTime;
    uint64_t    _timeOfLastMessage;
    uint64_t    _idleTimePeriod;
    double      _efficiencyThreshold;
    double      _windowSizeIncrement;
    double      _windowSize;
    double      _maxWindowSize;
    double      _minWindowSize;
    double      _decrementFactor;
    double      _windowSizeBackOff;
    double      _weight;
    double      _localMaxThroughput;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<DynamicThrottlePolicy> UP;
    typedef std::shared_ptr<DynamicThrottlePolicy> SP;

    /**
     * Constructs a new instance of this policy and sets the appropriate default values of member data.
     */
    DynamicThrottlePolicy();

    /**
     * Constructs a new instance of this policy and sets the appropriate default values of member data.
     *
     * @param windowSizeIncrement Initial value for window size increment. Also used
     * to set initial values for current window size and minimum window size.
     */
    DynamicThrottlePolicy(double windowSizeIncrement);

    /**
     * Constructs a new instance of this class using the given clock to calculate efficiency.
     *
     * @param timer The timer to use.
     */
    DynamicThrottlePolicy(ITimer::UP timer);

    /**
     * Sets the lower efficiency threshold at which the algorithm should perform window size back
     * off. Efficiency is the correlation between throughput and window size. The algorithm will increase the
     * window size until efficiency drops below the efficiency of the local maxima times this value.
     *
     * @param efficiencyThreshold The limit to set.
     * @return This, to allow chaining.
     * @see #setWindowSizeBackOff(double)
     */
    DynamicThrottlePolicy &setEfficiencyThreshold(double efficiencyThreshold);

    /**
     * Sets the step size used when increasing window size.
     *
     * @param windowSizeIncrement The step size to set.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setWindowSizeIncrement(double windowSizeIncrement);

    /**
     * Sets the factor of window size to back off to when the algorithm determines that efficiency is not
     * increasing.  A value of 1 means that there is no back off from the local maxima, and means that the
     * algorithm will fail to reduce window size to something lower than a previous maxima. This value is
     * capped to the [0, 1] range.
     *
     * @param windowSizeBackOff The back off to set.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setWindowSizeBackOff(double windowSizeBackOff);

    /**
     * Sets the rate at which the window size is updated. The larger the value, the less responsive the
     * resizing becomes. However, the smaller the value, the less accurate the measurements become.
     *
     * @param resizeRate The rate to set.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setResizeRate(double resizeRate);

    /**
     * Sets the weight for this client. The larger the value, the more resources
     * will be allocated to this clients. Resources are shared between clients
     * proportiannally to their weights.
     *
     * @param weight The weight to set.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setWeight(double weight);

    /**
     * Sets the idle time period for this client. If nothing is sent trhoughout
     * this time period, the dynamic window will retract.
     *
     * @param period The time period to set.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setIdleTimePeriod(uint64_t period);

    /**
     * Sets the maximium number of pending operations allowed at any time, in
     * order to avoid using too much resources.
     *
     * @param max The max to set.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setMaxWindowSize(double max);

    /**
     * Sets the maximum number of pending messages allowed.
     *
     * @param maxCount The max count.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setMaxPendingCount(uint32_t maxCount);

    /**
     * Get the maximum number of pending operations allowed at any time.
     *
     * @return The maximum number of operations.
     */
    double getMaxWindowSize() const { return _maxWindowSize; }

    /**
     * Sets the minimium number of pending operations allowed at any time, in
     * order to keep a level of performance.
     *
     * @param min The min to set.
     * @return This, to allow chaining.
     */
    DynamicThrottlePolicy &setMinWindowSize(double min);

    /**
    * Sets the relative step size when decreasing window size.
    *
    * @param decrementFactor the step size to set
    * @return this, to allow chaining
    */
    DynamicThrottlePolicy& setWindowSizeDecrementFactor(double decrementFactor);

    /**
     * Get the minimum number of pending operations allowed at any time.
     *
     * @return The minimum number of operations.
     */
    double getMinWindowSize() const { return _minWindowSize; }

    /**
     * Returns the maximum number of pending messages allowed.
     *
     * @return The max limit.
     */
    uint32_t getMaxPendingCount() const { return (uint32_t)_windowSize; }

    bool canSend(const Message &msg, uint32_t pendingCount) override;
    void processMessage(Message &msg) override;
    void processReply(Reply &reply) override;
};

} // namespace mbus

