// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import com.yahoo.container.di.componentgraph.Provider;

import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 * @deprecated Do not use!
 */
@Deprecated
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
