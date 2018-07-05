// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.concurrent.Timer;
import com.yahoo.log.LogLevel;
import java.util.logging.Logger;

/**
 * This is an implementatin of the {@link ThrottlePolicy} that offers dynamic limits to the number of pending messages a
 * {@link SourceSession} is allowed to have.
 *
 * <b>NOTE:</b> By context, "pending" is refering to the number of sent messages that have not been replied to yet.
 *
 * @author Simon Thoresen Hult
 */
public class DynamicThrottlePolicy extends StaticThrottlePolicy {

    private static final long IDLE_TIME_MILLIS = 60000;
    private final Timer timer;
    private int numSent = 0;
    private int numOk = 0;
    private double resizeRate = 3;
    private long resizeTime = 0;
    private long timeOfLastMessage;
    private double efficiencyThreshold = 1.0;
    private double windowSizeIncrement = 20;
    private double windowSize = windowSizeIncrement;
    private double minWindowSize = windowSizeIncrement;
    private double maxWindowSize = Integer.MAX_VALUE;
    private double windowSizeBackOff = 0.9;
    private double weight = 1.0;
    private double localMaxThroughput = 0;
    private double maxThroughput = 0;
    private static final Logger log = Logger.getLogger(DynamicThrottlePolicy.class.getName());

    /**
     * Constructs a new instance of this policy and sets the appropriate default values of member data.
     */
    public DynamicThrottlePolicy() {
        this(SystemTimer.INSTANCE);
    }

    /**
     * Constructs a new instance of this class using the given clock to calculate efficiency.
     *
     * @param timer The timer to use.
     */
    public DynamicThrottlePolicy(Timer timer) {
        this.timer = timer;
        this.timeOfLastMessage = timer.milliTime();
    }

    public double getWindowSizeIncrement() {
        return windowSizeIncrement;
    }

    public double getWindowSizeBackOff() {
        return windowSizeBackOff;
    }

    public void setMaxThroughput(double maxThroughput) {
        this.maxThroughput = maxThroughput;
    }

    @Override
    public boolean canSend(Message msg, int pendingCount) {
        if (!super.canSend(msg, pendingCount)) {
             return false;
        }
        long time = timer.milliTime();
        double elapsed = (time - timeOfLastMessage);
        if (elapsed > IDLE_TIME_MILLIS) {
            windowSize = Math.min(windowSize, pendingCount + windowSizeIncrement);
        }
        timeOfLastMessage = time;
        return pendingCount < windowSize;
    }

    @Override
    public void processMessage(Message msg) {
        super.processMessage(msg);
        if (++numSent < windowSize * resizeRate) {
            return;
        }

        long time = timer.milliTime();
        double elapsed = time - resizeTime;
        resizeTime = time;

        double throughput = numOk / elapsed;
        numSent = 0;
        numOk = 0;

        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "windowSize " + windowSize + " throughput " + throughput);
        }

        if (maxThroughput > 0 && throughput > maxThroughput * 0.95) {
            // No need to increase window when we're this close to max.
        } else if (throughput > localMaxThroughput * 1.01) {
            localMaxThroughput = throughput;
            windowSize += weight*windowSizeIncrement;
        } else {
            // scale up/down throughput for comparing to window size
            double period = 1;
            while(throughput * period/windowSize < 2) {
                period *= 10;
            }
            while(throughput * period/windowSize > 2) {
                period *= 0.1;
            }
            double efficiency = throughput*period/windowSize;
            if (efficiency < efficiencyThreshold) {
                double newSize = Math.min(windowSize,throughput * period);
                windowSize = Math.min(windowSize * windowSizeBackOff, windowSize - 2* windowSizeIncrement);
                localMaxThroughput = 0;
            } else {
                windowSize += weight*windowSizeIncrement;
            }
        }
        windowSize = Math.max(minWindowSize, windowSize);
        windowSize = Math.min(maxWindowSize, windowSize);
    }

    @Override
    public void processReply(Reply reply) {
        super.processReply(reply);
        if (!reply.hasErrors()) {
            ++numOk;
        }
    }

    /**
     * Sets the lower efficiency threshold at which the algorithm should perform window size back off. Efficiency is
     * the correlation between throughput and window size. The algorithm will increase the window size until efficiency
     * drops below the efficiency of the local maxima times this value.
     *
     * @param efficiencyThreshold The limit to set.
     * @return This, to allow chaining.
     * @see #setWindowSizeBackOff(double)
     */
    public DynamicThrottlePolicy setEfficiencyThreshold(double efficiencyThreshold) {
        this.efficiencyThreshold = efficiencyThreshold;
        return this;
    }

    /**
     * Sets the step size used when increasing window size.
     *
     * @param windowSizeIncrement The step size to set.
     * @return This, to allow chaining.
     */
    public DynamicThrottlePolicy setWindowSizeIncrement(double windowSizeIncrement) {
        this.windowSizeIncrement = windowSizeIncrement;
        return this;
    }

    /**
     * Sets the factor of window size to back off to when the algorithm determines that efficiency is not increasing.
     * A value of 1 means that there is no back off from the local maxima, and means that the algorithm will fail to
     * reduce window size to something lower than a previous maxima. This value is capped to the [0, 1] range.
     *
     * @param windowSizeBackOff The back off to set.
     * @return This, to allow chaining.
     */
    public DynamicThrottlePolicy setWindowSizeBackOff(double windowSizeBackOff) {
        this.windowSizeBackOff = Math.max(0, Math.min(1, windowSizeBackOff));
        return this;
    }

    /**
     * Sets the rate at which the window size is updated. The larger the value, the less responsive the resizing
     * becomes. However, the smaller the value, the less accurate the measurements become.
     *
     * @param resizeRate The rate to set.
     * @return This, to allow chaining.
     */
    public DynamicThrottlePolicy setResizeRate(double resizeRate) {
        this.resizeRate = resizeRate;
        return this;
    }

    /**
     * Sets the weight for this client. The larger the value, the more resources
     * will be allocated to this clients. Resources are shared between clients
     * proportiannally to their weights.
     *
     * @param weight The weight to set.
     * @return This, to allow chaining.
     */
    public DynamicThrottlePolicy setWeight(double weight) {
        this.weight = weight;
        return this;
    }

    /**
     * Sets the maximium number of pending operations allowed at any time, in
     * order to avoid using too much resources.
     *
     * @param max The max to set.
     * @return This, to allow chaining.
     */
    public DynamicThrottlePolicy setMaxWindowSize(double max) {
        this.maxWindowSize = max;
        return this;
    }

    /**
     * Get the maximum number of pending operations allowed at any time.
     *
     * @return The maximum number of operations.
     */
    public double getMaxWindowSize() {
        return maxWindowSize;
    }


    /**
     * Sets the minimium number of pending operations allowed at any time, in
     * order to keep a level of performance.
     *
     * @param min The min to set.
     * @return This, to allow chaining.
     */
    public DynamicThrottlePolicy setMinWindowSize(double min) {
        this.minWindowSize = min;
        return this;
    }

    /**
     * Get the minimum number of pending operations allowed at any time.
     *
     * @return The minimum number of operations.
     */
    public double getMinWindowSize() {
        return minWindowSize;
    }

    public DynamicThrottlePolicy setMaxPendingCount(int maxCount) {
        super.setMaxPendingCount(maxCount);
        maxWindowSize = maxCount;
        return this;
    }


    /**
     * Returns the maximum number of pending messages allowed.
     *
     * @return The max limit.
     */
    public int getMaxPendingCount() {
        return (int)windowSize;
    }

}
