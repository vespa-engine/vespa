// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.osgi;

import org.osgi.framework.Bundle;

import java.util.Collection;

/**
 * @author ollivir
 */
public class BundleClasses {
    private final Bundle bundle;
    private final Collection<String> classEntries;

    public BundleClasses(Bundle bundle, Collection<String> classEntries) {
        this.bundle = bundle;
        this.classEntries = classEntries;
    }

    public Bundle bundle() {
        return bundle;
    }

    public Collection<String> classEntries() {
        return classEntries;
    }
}
