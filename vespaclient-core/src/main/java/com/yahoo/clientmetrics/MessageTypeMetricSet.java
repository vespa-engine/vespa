// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.clientmetrics;

import com.yahoo.documentapi.messagebus.protocol.DocumentIgnoredReply;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.messagebus.Error;
import com.yahoo.metrics.CountMetric;
import com.yahoo.metrics.Metric;
import com.yahoo.metrics.MetricSet;
import com.yahoo.metrics.SimpleMetricSet;
import com.yahoo.metrics.SumMetric;
import com.yahoo.metrics.ValueMetric;

import java.util.stream.Stream;

/**
* @author thomasg
*/
public class MessageTypeMetricSet extends MetricSet {
    ValueMetric<Long> latency;
    CountMetric count;
    CountMetric ignored;

    SumMetric errorSum;
    MetricSet errors;
    String msgName;

    class ErrorMetric extends CountMetric {
        ErrorMetric(String name, MetricSet owner) {
            super(name, "", "Number of errors of type " + name, owner);
        }

        ErrorMetric(ErrorMetric other, CopyType copyType, MetricSet owner) {
            super(other, copyType, owner);
        }

        @Override
        public String getXMLTag() {
            return "error";
        }

        @Override
        public Metric clone(CopyType type, MetricSet owner, boolean includeUnused) {
            return new ErrorMetric(this, type, owner);
        }

    }

    public MessageTypeMetricSet(String msgName, MetricSet owner) {
        super(msgName.toLowerCase(), "", "", owner);
        this.msgName = msgName;
        latency = new ValueMetric<Long>("latency", "", "Latency (in ms)", this).averageMetric();
        count = new CountMetric("count", "", "Number received", this);
        ignored = new CountMetric("ignored", "", "Number ignored due to no matching document routing selectors", this);
        errors = new SimpleMetricSet("errors", "", "The errors returned", this);
        errorSum = new SumMetric("total", "", "Total number of errors", errors);
    }

    public MessageTypeMetricSet(MessageTypeMetricSet source, CopyType copyType, MetricSet owner, boolean includeUnused) {
        super(source, copyType, owner, includeUnused);
        msgName = source.msgName;
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
        String error = DocumentProtocol.getErrorName(r.getError(0).getCode());
        CountMetric s = (CountMetric)errors.getMetric(error);
        if (s == null) {
            s = new ErrorMetric(error, errors);
            errorSum.addMetricToSum(s);
        }
        s.inc();
    }

    private void updateSuccessMetrics(Reply r) {
        if (!(r instanceof DocumentIgnoredReply)) {
            if (r.getMessage().getTimeReceived() != 0) {
                latency.addValue(SystemTimer.INSTANCE.milliTime() - r.getMessage().getTimeReceived());
            }
            count.inc();
        } else {
            ignored.inc();
        }
    }

    @Override
    public Metric clone(CopyType type, MetricSet owner, boolean includeUnused)
        { return new MessageTypeMetricSet(this, type, owner, includeUnused); }

    /**
     * Returns true if every error in a stream is a test and set condition failed
     */
    private static boolean onlyTestAndSetConditionFailed(Stream<Error> errors) {
        return errors.allMatch(e -> e.getCode() == DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED);
    }
}
