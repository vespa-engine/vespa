// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Einar M R Rosenvinge
 */
public class ContainerDocumentApi {

    private static final String vespaClientBundleSpecification = "vespaclient-container-plugin";
    private final Options options;

    public ContainerDocumentApi(ContainerCluster cluster, Options options) {
        this.options = options;
        setupHandlers(cluster);
    }

    private void setupHandlers(ContainerCluster cluster) {
        cluster.addComponent(newVespaClientHandler("com.yahoo.document.restapi.resource.RestApi", "document/v1/*"));
        cluster.addComponent(newVespaClientHandler("com.yahoo.vespa.http.server.FeedHandler", ContainerCluster.RESERVED_URI_PREFIX + "/feedapi"));
    }

    private Handler<AbstractConfigProducer<?>> newVespaClientHandler(String componentId, String bindingSuffix) {
        Handler<AbstractConfigProducer<?>> handler = new Handler<>(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(componentId, null, vespaClientBundleSpecification), ""));

        for (String rootBinding : options.bindings) {
            handler.addServerBindings(rootBinding + bindingSuffix,
                    rootBinding + bindingSuffix + '/');
        }
        return handler;
    }

    public static final class Options {
        private final Collection<String> bindings;


        public Options(Collection<String> bindings) {
            this.bindings = Collections.unmodifiableCollection(bindings);
        }
    }

}
