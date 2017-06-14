// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.Handler;

/**
 * @author gjoranv
 * @since 5.6
 */
public class JerseyHandler extends Handler<AbstractConfigProducer<?>> {

    public static final String BUNDLE = "container-jersey";
    public static final String CLASS = "com.yahoo.container.jdisc.jersey.JerseyHandler";

    public JerseyHandler(String bindingPath) {
        super(new ComponentModel(bundleSpec(CLASS, BUNDLE, bindingPath)));
    }

    public static BundleInstantiationSpecification bundleSpec(String className, String bundle, String bindingPath) {
        return BundleInstantiationSpecification.getFromStrings(
                className + "-" + RestApi.idFromPath(bindingPath),
                className,
                bundle);
    }
}
