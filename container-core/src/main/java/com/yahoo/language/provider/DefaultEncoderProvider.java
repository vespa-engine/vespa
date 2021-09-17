// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.provider;

import com.google.inject.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.language.Language;
import com.yahoo.language.process.Encoder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

/**
 * Provides the default encoder implementation if no encoder component has been explicitly configured
 * (dependency injection will fallback to providers if no components of the requested type is found).
 *
 * @author bratseth
 */
@SuppressWarnings("unused") // Injected
public class DefaultEncoderProvider implements Provider<Encoder> {

    // Use lazy initialization to avoid expensive (memory-wise) instantiation
    private static final Encoder failingEncoder = new FailingEncoder();

    @Inject
    public DefaultEncoderProvider() { }

    @Override
    public Encoder get() { return failingEncoder; }

    @Override
    public void deconstruct() {}

    public static class FailingEncoder implements Encoder {

        @Override
        public List<Integer> encode(String text, Language language) {
            throw new IllegalStateException("No encoder has been configured");
        }

        @Override
        public Tensor encode(String text, Language language, TensorType tensorType) {
            throw new IllegalStateException("No encoder has been configured");
        }

    }

}
