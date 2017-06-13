// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.16.0
 */
public class JettyHttpServer extends SimpleComponent implements ServerConfig.Producer {

    private List<ConnectorFactory> connectorFactories = new ArrayList<>();

    public JettyHttpServer(ComponentId id) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(id,
                                                     fromString("com.yahoo.jdisc.http.server.jetty.JettyHttpServer"),
                                                     fromString("jdisc_http_service"))
        ));
        final FilterBindingsProviderComponent filterBindingsProviderComponent = new FilterBindingsProviderComponent(id);
        addChild(filterBindingsProviderComponent);
        inject(filterBindingsProviderComponent);
    }

    public void addConnector(ConnectorFactory connectorFactory) {
        connectorFactories.add(connectorFactory);
        addChild(connectorFactory);
    }

    public void removeConnector(ConnectorFactory connectorFactory) {
        if (connectorFactory == null) {
            return;
        }
        removeChild(connectorFactory);
        connectorFactories.remove(connectorFactory);
    }

    public List<ConnectorFactory> getConnectorFactories() {
        return Collections.unmodifiableList(connectorFactories);
    }

    @Override
    public void getConfig(ServerConfig.Builder builder) {
    }

    static ComponentModel providerComponentModel(final ComponentId parentId, String className) {
        final ComponentSpecification classNameSpec = new ComponentSpecification(
                className);
        return new ComponentModel(new BundleInstantiationSpecification(
                classNameSpec.nestInNamespace(parentId),
                classNameSpec,
                null));
    }

    public static final class FilterBindingsProviderComponent extends SimpleComponent {
        public FilterBindingsProviderComponent(final ComponentId parentId) {
            super(providerComponentModel(parentId, "com.yahoo.container.jdisc.FilterBindingsProvider"));
        }

    }

}
