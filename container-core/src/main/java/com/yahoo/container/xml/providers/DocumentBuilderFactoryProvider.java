// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 * @deprecated Do not use!
 */
@Deprecated
public class DocumentBuilderFactoryProvider implements Provider<DocumentBuilderFactory> {
    public static final String FACTORY_CLASS = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";

    @Override
    public DocumentBuilderFactory get() {
        return DocumentBuilderFactory.newInstance(FACTORY_CLASS,
                                                  this.getClass().getClassLoader());
    }

    @Override
    public void deconstruct() { }
}
