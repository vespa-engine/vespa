// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.model;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.osgi.provider.model.ComponentModel;

/**
 * Describes how a chained component should be created. Immutable.
 *
 * @author Arne Bergene Fossaa
 * @author Tony Vaagenes
 */
public class ChainedComponentModel extends ComponentModel {
    public final Dependencies dependencies;

    public ChainedComponentModel(BundleInstantiationSpecification bundleInstantiationSpec, Dependencies dependencies,
                                 String configId) {
        super(bundleInstantiationSpec, configId);
        assert(dependencies != null);

        this.dependencies = dependencies;
    }

    public ChainedComponentModel(BundleInstantiationSpecification bundleInstantiationSpec, Dependencies dependencies) {
        this(bundleInstantiationSpec, dependencies, null);
    }

}
