// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

/**
 * @author Einar M R Rosenvinge
 * @deprecated Do not use!
 */
@Deprecated // TODO: Remove on Vespa 8
public class DatatypeFactoryProvider implements Provider<DatatypeFactory> {

    public static final String FACTORY_CLASS = DatatypeFactory.DATATYPEFACTORY_IMPLEMENTATION_CLASS;

    @Override
    public DatatypeFactory get() {
        try {
            return DatatypeFactory.newInstance(FACTORY_CLASS, this.getClass().getClassLoader());
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void deconstruct() { }

}
