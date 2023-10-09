// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.concurrent.Timer;

import java.util.logging.Logger;

/**
 * Throttling policy that throttles sending based on a desired rate. It will
 * block messages if the current rate is higher than desired, but otherwise will
 * respect the static throttle policy's maximum window size.
 *
 * Rate is measured from at most the last 60 seconds.
 */
public class RateThrottlingPolicy extends StaticThrottlePolicy {

    public static final Logger log = Logger.getLogger(RateThrottlingPolicy.class.getName());

    long PERIOD = 1000;
    double desiredRate;

    double allotted = 0.0;
    long currentPeriod = 0;

    Timer timer;

    public RateThrottlingPolicy(double desiredRate) {
        this(desiredRate, SystemTimer.INSTANCE);
    }

    public RateThrottlingPolicy(double desiredRate, Timer timer) {
        this.desiredRate = desiredRate;
        this.timer = timer;
        currentPeriod = timer.milliTime() / PERIOD;
    }

    public boolean canSend(Message message, int pendingCount) {
        if (!super.canSend(message, pendingCount)) {
            return false;
        }

        long period = timer.milliTime() / PERIOD;

        while (currentPeriod < period) {
            if (allotted > 0) {
                allotted = 0.0;
            }

            allotted = allotted + PERIOD * desiredRate / 1000;
            currentPeriod++;
        }

        if (allotted > 0.0) {
            allotted -= 1;
            return true;
        }

        return false;
    }
}
