// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.concurrent.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is an implementation of the {@link ThrottlePolicy} that offers dynamic limits to the number of pending messages a
 * {@link SourceSession} is allowed to have. Pending means the number of sent messages that have not been replied to yet.
 * <p>
 * The algorithm works by increasing the number of messages allowed to be pending, the <em>window size</em>, until
 * this no longer increases throughput. At this point, the algorithm is driven by synthetic attraction towards latencies
 * which satisfy <code>log10(1 / latency) % 1 = e</code>, for some constant <code>0 &lt; e &lt; 1</code>. Weird? Most certainly!
 * </p><p>
 * The effect is that the window size the algorithm produces, for a saturated ideal server, has a level for each power
 * of ten with an attractor the window size tends towards while on this level, determined by the <code>e</code> above.
 * The {@link #setEfficiencyThreshold} determines the <code>e</code> constant. When <code>e</code> is set so the
 * attractor is close to the start of the interval, this has an inhibiting effect on the algorithm, and it is basically
 * reduced to "increase window size until this no longer increases throughput enough that it defeats the random noise".
 * As the attractor moves towards the end of the intervals, the algorithm becomes increasingly eager in increasing
 * its window size past what it measures as effective — if moved to the very end of the interval, the algorithm explodes.
 * The default setting has the attractor at <code>log10(2)</code> of the way from start to end of these levels.
 * </p><p>
 * Because the algorithm stops increasing the window size when increases in throughput drown in random variations, the
 * {@link #setWindowSizeIncrement} directly influences the efficient work domain of the algorithm. With the default
 * setting of <code>20</code>, it seems to struggle to increase past window sizes of a couple thousand. Using a static
 * increment (and a backoff factor) seems to be necessary to effectively balance the load different, competing policies
 * are allowed to produce.
 * </p><p>
 * When there are multiple policies that compete against each other, they will increase load until saturating the server.
 * If keeping all other policies but one fixed, this one policy would still see an increase in throughput with increased
 * window size, even with a saturated server, as it would be given a greater share of the server's resources. However,
 * since all algorithms adjust their windows concurrently, they will all reduce the throughput of the other algorithms.
 * The result is that the commonly see the server as saturated, and commonly reach the behaviour where some increases in
 * window size lead to measurable throughput gains, while others don't.
 * </p><p>
 * Now the weighting ({@link #setWeight} comes into play: with equals weights, two algorithms would have a break-even
 * between being governed by the attractors (above), which eventually limits window size, and increases due to positive
 * measurements, at the same point along the window size axis. With smaller weights, i.e., smaller increases to window
 * size, this break-even occurs where the curve is steeper, i.e., where the client has a smaller share of the server.
 * Thus, competing algorithms with different weights end up with a resource distribution roughly proportional to weight.
 * </p>
 *
 * @author Simon Thoresen Hult
 * @author jonmv
 */
public class DynamicThrottlePolicy extends StaticThrottlePolicy {

    private static final long IDLE_TIME_MILLIS = 60000;
    private final Timer timer;
    private int numSent = 0;
    private int numOk = 0;
    private double resizeRate = 3;
    private long resizeTime = 0;
    private long timeOfLastMessage;
    private double efficiencyThreshold = 1;
    private double windowSizeIncrement = 20;
    private double windowSize = windowSizeIncrement;
    private double minWindowSize = windowSizeIncrement;
    private double decrementFactor = 2.0;
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
     * @param timer the timer to use
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

    public DynamicThrottlePolicy setMaxThroughput(double maxThroughput) {
        this.maxThroughput = maxThroughput;
        return this;
    }

    @Override
    public boolean canSend(Message message, int pendingCount) {
        if ( ! super.canSend(message, pendingCount)) {
             return false;
        }
        long time = timer.milliTime();
        double elapsed = (time - timeOfLastMessage);
        if (elapsed > IDLE_TIME_MILLIS) {
            windowSize = Math.max(minWindowSize, Math.min(windowSize, pendingCount + windowSizeIncrement));
        }
        timeOfLastMessage = time;
        int windowSizeFloored = (int) windowSize;
        // Use floating point window sizes, so the algorithm sees the difference between 1.1 and 1.9 window size.
        boolean carry = numSent < (windowSize * resizeRate) * (windowSize - windowSizeFloored);
        return pendingCount < windowSizeFloored + (carry ? 1 : 0);
    }

    @Override
    public void processMessage(Message message) {
        super.processMessage(message);
        if (++numSent < windowSize * resizeRate) {
            return;
        }

        long time = timer.milliTime();
        double elapsed = time - resizeTime;
        resizeTime = time;

        double throughput = numOk / elapsed;
        numSent = 0;
        numOk = 0;

        if (maxThroughput > 0 && throughput > maxThroughput * 0.95) {
            // No need to increase window when we're this close to max.
            // TODO jonmv: Not so sure — what if we're too high, and should back off?
        } else if (throughput > localMaxThroughput) {
            localMaxThroughput = throughput;
            windowSize += weight * windowSizeIncrement;
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "windowSize " + windowSize + " throughput " + throughput + " local max " + localMaxThroughput);
            }
        } else {
            // scale up/down throughput for comparing to window size
            double period = 1;
            while(throughput * period / windowSize < 2) {
                period *= 10;
            }
            while(throughput * period / windowSize > 2) {
                period *= 0.1;
            }
            double efficiency = throughput * period / windowSize; // "efficiency" is a strange name. This is where on the level it is.
            if (efficiency < efficiencyThreshold) {
                windowSize = Math.min(windowSize * windowSizeBackOff, windowSize - decrementFactor * windowSizeIncrement);
                localMaxThroughput = 0;
            } else {
                windowSize += weight * windowSizeIncrement;
            }
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "windowSize " + windowSize + " throughput " + throughput + " local max " + localMaxThroughput + " efficiency " + efficiency);
            }
        }
        windowSize = Math.max(minWindowSize, windowSize);
        windowSize = Math.min(maxWindowSize, windowSize);
    }

    @Override
    public void processReply(Reply reply) {
        super.processReply(reply);
        if ( ! reply.hasErrors()) {
            ++numOk;
        }
    }

    /**
     * Determines where on each latency level the attractor sits. 2 is at the very end, and makes this to *boom*.
     * 0.2 is at the very start, and makes the algorithm more conservative. Probably fine to stay away from this.
     */
    // Original javadoc is non-sense, but kept for historical reasons.
    /*
     * Sets the lower efficiency threshold at which the algorithm should perform window size back off. Efficiency is
     * the correlation between throughput and window size. The algorithm will increase the window size until efficiency
     * drops below the efficiency of the local maxima times this value.
     *
     * @param efficiencyThreshold the limit to set
     * @return this, to allow chaining
     * @see #setWindowSizeBackOff(double)
     */
    public DynamicThrottlePolicy setEfficiencyThreshold(double efficiencyThreshold) {
        this.efficiencyThreshold = efficiencyThreshold;
        return this;
    }

    /**
     * Sets the step size used when increasing window size.
     *
     * @param windowSizeIncrement the step size to set
     * @return this, to allow chaining
     */
    public DynamicThrottlePolicy setWindowSizeIncrement(double windowSizeIncrement) {
        this.windowSizeIncrement = windowSizeIncrement;
        this.windowSize = Math.max(this.minWindowSize, this.windowSizeIncrement);
        return this;
    }

    /**
     * Sets the relative step size when decreasing window size.
     *
     * @param decrementFactor the step size to set
     * @return this, to allow chaining
     */
    public DynamicThrottlePolicy setWindowSizeDecrementFactor(double decrementFactor) {
        this.decrementFactor = decrementFactor;
        return this;
    }

    /**
     * Sets the factor of window size to back off to when the algorithm determines that efficiency is not increasing.
     * Capped to [0, 1]
     *
     * @param windowSizeBackOff the back off to set
     * @return this, to allow chaining
     */
    public DynamicThrottlePolicy setWindowSizeBackOff(double windowSizeBackOff) {
        this.windowSizeBackOff = Math.max(0, Math.min(1, windowSizeBackOff));
        return this;
    }

    /**
     * Sets the rate at which the window size is updated. The larger the value, the less responsive the resizing
     * becomes. However, the smaller the value, the less accurate the measurements become. Capped to [2, )
     *
     * @param resizeRate the rate to set
     * @return this, to allow chaining
     */
    public DynamicThrottlePolicy setResizeRate(double resizeRate) {
        this.resizeRate = Math.max(2, resizeRate);
        return this;
    }

    /**
     * Sets the weight for this client. The larger the value, the more resources
     * will be allocated to this clients. Resources are shared between clients roughly
     * proportionally to the set weights. Must be a positive number.
     *
     * @param weight the weight to set
     * @return this, to allow chaining
     */
    public DynamicThrottlePolicy setWeight(double weight) {
        this.weight = Math.pow(weight, 0.5);
        return this;
    }

    /**
     * Sets the maximum number of pending operations allowed at any time, in
     * order to avoid using too much resources.
     *
     * @param max the max to set
     * @return this, to allow chaining
     */
    public DynamicThrottlePolicy setMaxWindowSize(double max) {
        if (max < 1)
            throw new IllegalArgumentException("Maximum window size cannot be less than one");

        this.maxWindowSize = max;
        return this;
    }

    /**
     * Get the maximum number of pending operations allowed at any time.
     *
     * @return the maximum number of operations
     */
    public double getMaxWindowSize() {
        return maxWindowSize;
    }


    /**
     * Sets the minimum number of pending operations allowed at any time, in
     * order to keep a level of performance.
     *
     * @param min the min to set
     * @return this, to allow chaining
     */
    public DynamicThrottlePolicy setMinWindowSize(double min) {
        if (min < 1)
            throw new IllegalArgumentException("Minimum window size cannot be less than one");

        this.minWindowSize = min;
        this.windowSize = Math.max(this.minWindowSize, this.windowSizeIncrement);
        return this;
    }

    /**
     * Get the minimum number of pending operations allowed at any time.
     *
     * @return the minimum number of operations
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
     * @return the max limit
     */
    public int getMaxPendingCount() {
        return (int) windowSize;
    }

    double getWindowSize() { return windowSize; }

}
