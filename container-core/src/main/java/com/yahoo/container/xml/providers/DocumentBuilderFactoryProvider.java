// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * @author Einar M R Rosenvinge
 * @deprecated Do not use!
 */
@Deprecated // TODO: Remove on Vespa 8
public class DocumentBuilderFactoryProvider implements Provider<DocumentBuilderFactory> {

    public static final String FACTORY_CLASS = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";

    @Override
    public DocumentBuilderFactory get() {
        return DocumentBuilderFactory.newInstance(FACTORY_CLASS, this.getClass().getClassLoader());
    }

    @Override
    public void deconstruct() { }

}
