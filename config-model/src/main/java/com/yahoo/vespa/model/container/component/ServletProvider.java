// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.servlet.ServletConfigConfig;
import com.yahoo.osgi.provider.model.ComponentModel;

import java.util.Map;

/**
 * @author stiankri
 */
public class ServletProvider extends Servlet implements ServletConfigConfig.Producer {
    public static final String BUNDLE = "container-core";
    public static final String CLASS = "com.yahoo.container.servlet.ServletProvider";

    private static final ComponentId SERVLET_PROVIDER_NAMESPACE = ComponentId.fromString("servlet-provider");
    private final Map<String, String> servletConfig;

    public ServletProvider(SimpleComponent servletToProvide, String bindingPath, Map<String, String> servletConfig) {
        super(new ComponentModel(
                        new BundleInstantiationSpecification(servletToProvide.getComponentId().nestInNamespace(SERVLET_PROVIDER_NAMESPACE),
                                ComponentSpecification.fromString(CLASS),
                                ComponentSpecification.fromString(BUNDLE))),
              bindingPath);

        inject(servletToProvide);
        addChild(servletToProvide);
        this.servletConfig = servletConfig;
    }

    @Override
    public void getConfig(ServletConfigConfig.Builder builder) {
        builder.map(servletConfig);
    }
}
