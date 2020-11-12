// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * Some configuration level with no special handling of its own.
 *
 * @author arnej27959
 */
public final class SimpleConfigProducer<T extends AbstractConfigProducer<?>> extends AbstractConfigProducer<T> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance
     *
     * @param parent   parent ConfigProducer.
     * @param configId name of this instance
     */
    public SimpleConfigProducer(AbstractConfigProducer<?> parent, String configId) {
        super(parent, configId);
    }

    //Ease access restriction
    @Override
    public void addChild(T child) {
        super.addChild(child);
    }

}
