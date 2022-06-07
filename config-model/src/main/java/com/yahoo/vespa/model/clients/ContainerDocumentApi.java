// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ContainerDocumentApi {

    public static final String DOCUMENT_V1_PREFIX = "/document/v1";

    public ContainerDocumentApi(ContainerCluster<?> cluster, Options options) {
        addRestApiHandler(cluster, options);
    }

    private static void addRestApiHandler(ContainerCluster<?> cluster, Options options) {
        var handler = newVespaClientHandler("com.yahoo.document.restapi.resource.DocumentV1ApiHandler", DOCUMENT_V1_PREFIX + "/*", options);
        cluster.addComponent(handler);

        // We need to include a dummy implementation of the previous restapi handler (using the same class name).
        // The internal legacy test framework requires that the name of the old handler is listed in /ApplicationStatus.
        var oldHandlerDummy = handlerComponentSpecification("com.yahoo.document.restapi.resource.RestApi");
        cluster.addComponent(oldHandlerDummy);
    }

    private static Handler<AbstractConfigProducer<?>> newVespaClientHandler(String componentId,
                                                                            String bindingSuffix,
                                                                            Options options) {
        Handler<AbstractConfigProducer<?>> handler = handlerComponentSpecification(componentId);
        if (options.bindings.isEmpty()) {
            handler.addServerBindings(
                    SystemBindingPattern.fromHttpPath(bindingSuffix),
                    SystemBindingPattern.fromHttpPath(bindingSuffix + '/'));
        } else {
            for (String rootBinding : options.bindings) {
                String pathWithoutLeadingSlash = bindingSuffix.substring(1);
                handler.addServerBindings(
                        UserBindingPattern.fromPattern(rootBinding + pathWithoutLeadingSlash),
                        UserBindingPattern.fromPattern(rootBinding + pathWithoutLeadingSlash + '/'));
            }
        }
        return handler;
    }

    private static Handler<AbstractConfigProducer<?>> handlerComponentSpecification(String className) {
        return new Handler<>(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(className, null, "vespaclient-container-plugin"), ""));
    }

    public static final class Options {

        private final Collection<String> bindings;

        public Options(Collection<String> bindings) {
            this.bindings = Collections.unmodifiableCollection(bindings);
        }
    }

}
