// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.stream.XMLOutputFactory;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 * @deprecated Do not use!
 */
@Deprecated
public class XMLOutputFactoryProvider implements Provider<XMLOutputFactory> {
    public static final String FACTORY_CLASS = "com.sun.xml.internal.stream.XMLOutputFactoryImpl";
    @Override
    public XMLOutputFactory get() {
        System.setProperty("javax.xml.stream.XMLOutputFactory", FACTORY_CLASS);

        // NOTE: In case the newFactory(String, ClassLoader) is used, XMLOutputFactory treats the string as system
        //       property name. Also, the given class loader is ignored if the property is set!
        return XMLOutputFactory.newFactory();
    }

    @Override
    public void deconstruct() { }
}
