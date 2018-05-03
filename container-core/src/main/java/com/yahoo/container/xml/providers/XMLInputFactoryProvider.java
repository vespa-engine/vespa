// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.stream.XMLInputFactory;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 * @deprecated Do not use!
 */
@Deprecated
public class XMLInputFactoryProvider implements Provider<XMLInputFactory> {
    private static final String INPUT_FACTORY_INTERFACE = XMLInputFactory.class.getName();
    public static final String FACTORY_CLASS = "com.sun.xml.internal.stream.XMLInputFactoryImpl";

    @Override
    public XMLInputFactory get() {
        //ugly, but must be done
        System.setProperty(INPUT_FACTORY_INTERFACE, FACTORY_CLASS);

        // NOTE: In case the newFactory(String, ClassLoader) is used,
        //       the given class loader is ignored if the system property is set!
        return XMLInputFactory.newFactory();
    }

    @Override
    public void deconstruct() { }
}
