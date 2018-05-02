// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 * @deprecated Do not use!
 */
@Deprecated
@SuppressWarnings("deprecation")
public class JAXBContextFactoryProvider implements Provider<com.yahoo.container.xml.bind.JAXBContextFactory> {
    public static final String FACTORY_CLASS = com.yahoo.container.xml.bind.JAXBContextFactory.class.getName();

    @Override
    public com.yahoo.container.xml.bind.JAXBContextFactory get() {
        return new com.yahoo.container.xml.bind.JAXBContextFactory();
    }

    @Override
    public void deconstruct() { }
}
