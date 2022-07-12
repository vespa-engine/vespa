// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.provider.model;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.bundle.BundleInstantiationSpecification;

/**
 * Describes how a component should be created.
 *
 * Immutable
 *
 * @author gjoranv
 */
public class ComponentModel {

    public final BundleInstantiationSpecification bundleInstantiationSpec;
    public final String configId; // only used in the container, null when used in the model

    public ComponentModel(BundleInstantiationSpecification bundleInstantiationSpec, String configId) {
        if (bundleInstantiationSpec == null)
            throw new IllegalArgumentException("Null bundle instantiation spec!");

        this.bundleInstantiationSpec = bundleInstantiationSpec;
        this.configId = configId;
    }

    public ComponentModel(String idSpec, String classSpec, String bundleSpec, String configId) {
        this(BundleInstantiationSpecification.fromStrings(idSpec, classSpec, bundleSpec), configId);
    }

    // For vespamodel
    public ComponentModel(BundleInstantiationSpecification bundleInstantiationSpec) {
        this(bundleInstantiationSpec, null);
    }

    // For vespamodel
    public ComponentModel(String idSpec, String classSpec, String bundleSpec) {
        this(BundleInstantiationSpecification.fromStrings(idSpec, classSpec, bundleSpec));
    }

    public ComponentId getComponentId() {
        return bundleInstantiationSpec.id;
    }

    public ComponentSpecification getClassId() {
        return bundleInstantiationSpec.classId;
    }

}
