// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.xml.bind.JAXBContextFactory;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 */
public class JAXBContextFactoryProvider implements Provider<JAXBContextFactory> {
    public static final String FACTORY_CLASS = JAXBContextFactory.class.getName();

    @Override
    public JAXBContextFactory get() {
        return new JAXBContextFactory();
    }

    @Override
    public void deconstruct() { }
}
