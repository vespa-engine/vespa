// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;

/**
 * A component that resides in the container-disc bundle.
 *
 * @author gjoranv
 */
public class SimpleComponent extends Component<AnyConfigProducer, ComponentModel> {

    public SimpleComponent(ComponentModel model) {
        super(model);
    }

    public SimpleComponent(String className) {
        this(new ComponentModel(BundleInstantiationSpecification.fromStrings(className, null, null)));
    }

    public SimpleComponent(String id, String className) {
        this(new ComponentModel(BundleInstantiationSpecification.fromStrings(id, className, null)));
    }

}
