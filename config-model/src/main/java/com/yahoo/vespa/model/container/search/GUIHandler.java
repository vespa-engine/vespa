// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.Handler;


/**
 * @author  Henrik Høiness
 */
public class GUIHandler extends Handler<AbstractConfigProducer<?>> {

    public static final String BUNDLE = "container-search-gui";
    public static final String CLASS = "com.yahoo.search.query.gui.GUIHandler";
    public static final String BINDING_PATH = "/querybuilder/*";

    public GUIHandler() {
        super(new ComponentModel(bundleSpec(CLASS, BUNDLE)));
    }

    public static BundleInstantiationSpecification bundleSpec(String className, String bundle) {
        return BundleInstantiationSpecification.getFromStrings(className, className, bundle);
    }

}
