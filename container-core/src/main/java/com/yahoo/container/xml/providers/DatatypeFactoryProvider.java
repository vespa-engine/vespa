// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 * @deprecated Do not use!
 */
@Deprecated
public class DatatypeFactoryProvider implements Provider<DatatypeFactory> {
    public static final String FACTORY_CLASS = DatatypeFactory.DATATYPEFACTORY_IMPLEMENTATION_CLASS;

    @Override
    public DatatypeFactory get() {
        try {
            return DatatypeFactory.newInstance(
                    FACTORY_CLASS,
                    this.getClass().getClassLoader());
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void deconstruct() { }
}
