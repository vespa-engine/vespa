// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

/**
 * @author Einar M R Rosenvinge
 * @deprecated Do not use!
 */
@Deprecated // TODO: Remove on Vespa 8
public class XPathFactoryProvider implements Provider<XPathFactory> {

    public static final String FACTORY_CLASS = "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl";

    @Override
    public XPathFactory get() {
        try {
            return XPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI,
                                            FACTORY_CLASS,
                                            this.getClass().getClassLoader());
        } catch (XPathFactoryConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void deconstruct() { }

}
