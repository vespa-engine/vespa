// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * A metrics API with declarable metric, and also an implementation of the
 * JDisc Metrics API where the newest state is made continously available.
 *
 * <p>
 * Users should have an instance of {@link com.yahoo.metrics.simple.MetricReceiver}
 * injected in the constructor where needed, then declare metrics as instances
 * of {@link com.yahoo.metrics.simple.Counter} and
 * {@link com.yahoo.metrics.simple.Gauge} using
 * {@link com.yahoo.metrics.simple.MetricReceiver#declareCounter(String)},
 * {@link com.yahoo.metrics.simple.MetricReceiver#declareCounter(String, Point)},
 * {@link com.yahoo.metrics.simple.MetricReceiver#declareGauge(String)},
 * {@link com.yahoo.metrics.simple.MetricReceiver#declareGauge(String, Point)}, or
 * {@link com.yahoo.metrics.simple.MetricReceiver#declareGauge(String, java.util.Optional, MetricSettings)}.
 * </p>
 *
 * <p>
 * Clients input data through the API in {@link com.yahoo.metrics.simple.MetricReceiver},
 * while the internal work is done by {@link com.yahoo.metrics.simple.MetricAggregator}.
 * Initialization is done top-down from {@link com.yahoo.metrics.simple.MetricManager}.
 * The link between calls to MetricReceiver and MetricAggregator is the role of
 * {@link com.yahoo.metrics.simple.MetricUpdater}.
 * </p>
 *
 * @author Steinar Knutsen
 */
@PublicApi
@ExportPackage
package com.yahoo.metrics.simple;

import com.yahoo.api.annotations.PublicApi;
import com.yahoo.osgi.annotation.ExportPackage;
