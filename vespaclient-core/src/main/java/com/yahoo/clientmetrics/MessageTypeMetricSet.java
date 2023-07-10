// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.clientmetrics;

import com.yahoo.concurrent.Timer;
import com.yahoo.documentapi.messagebus.protocol.DocumentIgnoredReply;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.messagebus.Error;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
* @author Thomas Gundersen
*/
public class MessageTypeMetricSet {

    public long latency_total;
    public long latency_min = Long.MAX_VALUE;
    public long latency_max = Long.MIN_VALUE;
    public long count = 0;
    public long ignored = 0;
    public long errorCount = 0;
    public final Timer timer;
    private final Map<String, Long> errorCounts = new HashMap<>();

    private final String msgName;

    MessageTypeMetricSet(String msgName, Timer timer) {
        this.msgName = msgName;
        this.timer = timer;
    }

    public String getMessageName() {
        return msgName;
    }

    public void addReply(Reply r) {
        if (!r.hasErrors() || onlyTestAndSetConditionFailed(r.getErrors())) {
            updateSuccessMetrics(r);
        } else {
            updateFailureMetrics(r);
        }
    }

    private void updateFailureMetrics(Reply r) {
        errorCount++;
        String error = DocumentProtocol.getErrorName(r.getError(0).getCode());
        Long s = errorCounts.get(error);
        if (s == null) {
            errorCounts.put(error, 1L);
        } else {
            errorCounts.put(error, s+1);
        }
    }

    private void updateSuccessMetrics(Reply r) {
        if (!(r instanceof DocumentIgnoredReply)) {
            if (r.getMessage().getTimeReceived() != 0) {
                long latency = (timer.milliTime() - r.getMessage().getTimeReceived());
                latency_max = Math.max(latency_max, latency);
                latency_min = Math.min(latency_min, latency);
                latency_total += latency;
            }
            count++;
        } else {
            ignored++;
        }
    }

    /**
     * Returns true if every error in a stream is a test and set condition failed
     */
    private static boolean onlyTestAndSetConditionFailed(Stream<Error> errors) {
        return errors.allMatch(e -> e.getCode() == DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED);
    }
}
