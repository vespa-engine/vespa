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

    @Inject
    public DefaultEncoderProvider() { }

    @Override
    public Encoder get() { return Encoder.throwsOnUse; }

    @Override
    public void deconstruct() {}

}
