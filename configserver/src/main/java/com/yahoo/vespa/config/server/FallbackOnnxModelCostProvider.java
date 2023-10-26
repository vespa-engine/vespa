// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server;

import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.container.di.componentgraph.Provider;

/**
 * Default provider that provides a disabled {@link OnnxModelCost} instance.
 *
 * @author bjorncs
 */
public class FallbackOnnxModelCostProvider implements Provider<OnnxModelCost> {
    @Override public OnnxModelCost get() { return OnnxModelCost.disabled(); }
    @Override public void deconstruct() {}
}
