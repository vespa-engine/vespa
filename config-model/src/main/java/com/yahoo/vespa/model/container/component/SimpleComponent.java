// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * A component that only needs a simple ComponentModel.
 *
 * @author gjoranv
 * @since 5.1.9
 */
public class SimpleComponent extends Component<AbstractConfigProducer<?>, ComponentModel> {

    public SimpleComponent(ComponentModel model) {
        super(model);
    }

    // For a component that uses the class name as id, and resides in the container-disc bundle.
    public SimpleComponent(String className) {
        this(new ComponentModel(BundleInstantiationSpecification.getFromStrings(className, null, null)));
    }

}
