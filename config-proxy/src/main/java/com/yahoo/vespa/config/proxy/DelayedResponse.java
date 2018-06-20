// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Class that handles responses that are put on delayedResponses queue.  Implements the <code>Delayed</code>
 * interface to keep track of the order of responses (used when checking
 * delayed responses and returning requests that are about to time out, in cases where no new
 * config has been returned from upstream)
 *
 * @see DelayedResponseHandler
 */
public class DelayedResponse implements Delayed {

    private final JRTServerConfigRequest request;
    private final long returnTime;

    public DelayedResponse(JRTServerConfigRequest request) {
        this(request, System.currentTimeMillis() + request.getTimeout());
    }

    DelayedResponse(JRTServerConfigRequest request, long returnTime) {
        this.request = request;
        this.returnTime = returnTime;
    }

    public Long getReturnTime() {
        return returnTime;
    }

    public JRTServerConfigRequest getRequest() {
        return request;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return returnTime - System.currentTimeMillis();
    }

    @Override
    public int compareTo(Delayed delayed) {
        if (this == delayed) {
            return 0;
        }
        if (delayed instanceof com.yahoo.vespa.config.proxy.DelayedResponse) {
            if (this.returnTime < ((com.yahoo.vespa.config.proxy.DelayedResponse) delayed).getReturnTime()) {
                return -1;
            } else {
                return 1;
            }
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return request.getShortDescription() + ", delayLeft=" + getDelay(TimeUnit.MILLISECONDS) + " ms";
    }

}
