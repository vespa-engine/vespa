// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.FilterConfigProvider;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;

/**
 * This is only for the legacy certificate filter setup, outside http.
 *
 * TODO: Remove when 'filter' directly under 'jdisc' can be removed from services.xml
 *
 * @author Tony Vaagenes
 */
public class HttpFilter extends SimpleComponent {
    private static final ComponentSpecification filterConfigProviderClass =
            ComponentSpecification.fromString(FilterConfigProvider.class.getName());

    public final SimpleComponent filterConfigProvider;

    public HttpFilter(BundleInstantiationSpecification spec) {
        super(new ComponentModel(spec));

        filterConfigProvider = new SimpleComponent(new ComponentModel(
                new BundleInstantiationSpecification(configProviderId(spec.id), filterConfigProviderClass, null)));

        addChild(filterConfigProvider);
     }

    // public for testing
    public static ComponentId configProviderId(ComponentId filterId) {
        return ComponentId.fromString("filterConfig").nestInNamespace(filterId);
    }
}
