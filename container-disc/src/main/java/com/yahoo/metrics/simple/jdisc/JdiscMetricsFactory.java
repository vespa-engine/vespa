// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple.jdisc;

import java.io.PrintStream;
import java.util.logging.Logger;

import com.yahoo.container.jdisc.MetricConsumerFactory;
import com.yahoo.container.jdisc.state.MetricSnapshot;
import com.yahoo.container.jdisc.state.SnapshotProvider;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.metrics.simple.Bucket;
import com.yahoo.metrics.simple.MetricReceiver;

/**
 * A factory for all the JDisc API classes.
 *
 * @author Steinar Knutsen
 */
public class JdiscMetricsFactory implements MetricConsumerFactory, SnapshotProvider {

    private static final Logger log = Logger.getLogger(JdiscMetricsFactory.class.getName());
    private final SimpleMetricConsumer metricInstance;
    private final MetricReceiver metricReceiver;

    public JdiscMetricsFactory(MetricReceiver receiver) {
        this.metricReceiver = receiver;
        this.metricInstance = new SimpleMetricConsumer(receiver);
    }

    @Override
    public MetricConsumer newInstance() {
        // the underlying implementation is thread safe anyway to allow for stand-alone use
        return metricInstance;
    }


    @Override
    public MetricSnapshot latestSnapshot() {
        Bucket curr = metricReceiver.getSnapshot();
        if (curr == null) {
            log.warning("no snapshot from instance of " + metricReceiver.getClass());
            return null;
        } else {
            SnapshotConverter converter = new SnapshotConverter(curr);
            return converter.convert();
        }
    }

    @Override
    public void histogram(PrintStream output) {
        Bucket curr = metricReceiver.getSnapshot();
        if (curr == null) {
            log.warning("no snapshot from instance of " + metricReceiver.getClass());
        } else {
            SnapshotConverter converter = new SnapshotConverter(curr);
            converter.outputHistograms(output);
        }
    }

}
