// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.bind;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * Container components can take an instance of this class as a constructor argument,
 * to get a new instance injected by the container framework. There is usually no
 * need to create an instance with this class' constructor.
 * <p>
 * This factory is needed because the JAXBContext needs a user defined context path,
 * which means that it cannot be created at the time the container creates its
 * component graph.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @author gjoranv
 * @since 5.3
 * @deprecated Do not use!
 */
@Deprecated
public class JAXBContextFactory {
    public static final String FACTORY_CLASS = "com.sun.xml.internal.bind.v2.ContextFactory";

    /**
     * Returns a new JAXBContext for the context path defined by the given list of classes.
     * @return A new JAXBContext.
     * @param classes One class per package that contains schema derived classes and/or
     *                java to schema (JAXB-annotated) mapped classes
     */
    public JAXBContext newInstance(Class<?>... classes) {
        return newInstance(getContextPath(classes), classes[0].getClassLoader());
    }

    // TODO: guard against adding the same package more than once
    static String getContextPath(Class<?>... classes) {
        if (classes == null || classes.length == 0) {
            throw new IllegalArgumentException("Empty package list.");
        }
        StringBuilder contextPath = new StringBuilder();
        for (Class<?> clazz : classes) {
            contextPath
                    .append(clazz.getPackage().getName())
                    .append(':');
        }
        contextPath.deleteCharAt(contextPath.length() - 1);
        return contextPath.toString();
    }

    private static JAXBContext newInstance(String contextPath, ClassLoader classLoader) {
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, FACTORY_CLASS);
        try {
            return JAXBContext.newInstance(contextPath, classLoader);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }
}
