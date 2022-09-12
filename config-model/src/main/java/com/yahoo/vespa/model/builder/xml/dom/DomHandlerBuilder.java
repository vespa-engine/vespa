// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.xml.BundleInstantiationSpecificationBuilder;
import org.w3c.dom.Element;

import java.util.OptionalInt;
import java.util.Set;

import static com.yahoo.vespa.model.container.ApplicationContainerCluster.METRICS_V2_HANDLER_BINDING_1;
import static com.yahoo.vespa.model.container.ApplicationContainerCluster.METRICS_V2_HANDLER_BINDING_2;
import static com.yahoo.vespa.model.container.ContainerCluster.STATE_HANDLER_BINDING_1;
import static com.yahoo.vespa.model.container.ContainerCluster.STATE_HANDLER_BINDING_2;
import static com.yahoo.vespa.model.container.ContainerCluster.VIP_HANDLER_BINDING;
import static java.util.logging.Level.INFO;

/**
 * @author gjoranv
 */
public class DomHandlerBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Handler> {

    private static final Set<BindingPattern> reservedBindings =
            Set.of(METRICS_V2_HANDLER_BINDING_1,
                   METRICS_V2_HANDLER_BINDING_2,
                   STATE_HANDLER_BINDING_1,
                   STATE_HANDLER_BINDING_2,
                   VIP_HANDLER_BINDING);

    private final ApplicationContainerCluster cluster;
    private OptionalInt portBindingOverride;

    public DomHandlerBuilder(ApplicationContainerCluster cluster) {
        this(cluster, OptionalInt.empty());
    }
    public DomHandlerBuilder(ApplicationContainerCluster cluster, OptionalInt portBindingOverride) {
        this.cluster = cluster;
        this.portBindingOverride = portBindingOverride;
    }

    @Override
    protected Handler doBuild(DeployState deployState, AbstractConfigProducer<?> parent, Element handlerElement) {
        Handler handler = createHandler(handlerElement);
        OptionalInt port = portBindingOverride.isPresent() && deployState.isHosted() && deployState.featureFlags().useRestrictedDataPlaneBindings()
                ? portBindingOverride
                : OptionalInt.empty();

        for (Element binding : XML.getChildren(handlerElement, "binding"))
            addServerBinding(handler, userBindingPattern(XML.getValue(binding), port), deployState.getDeployLogger());

        DomComponentBuilder.addChildren(deployState, parent, handlerElement, handler);

        return handler;
    }

    private static UserBindingPattern userBindingPattern(String path, OptionalInt port) {
        UserBindingPattern bindingPattern = UserBindingPattern.fromPattern(path);
        return port.isPresent()
                ? bindingPattern.withPort(port.getAsInt())
                : bindingPattern;
    }

    Handler createHandler(Element handlerElement) {
        BundleInstantiationSpecification bundleSpec = BundleInstantiationSpecificationBuilder.build(handlerElement);
        return new Handler(new ComponentModel(bundleSpec));
    }

    private void addServerBinding(Handler handler, BindingPattern binding, DeployLogger log) {
        throwIfBindingIsReserved(binding, handler);
        handler.addServerBindings(binding);
        removeExistingServerBinding(binding, handler, log);
    }

    private void throwIfBindingIsReserved(BindingPattern binding, Handler newHandler) {
        for (var reserved : reservedBindings) {
            if (binding.hasSamePattern(reserved)) {
                throw new IllegalArgumentException("Binding '" + binding.patternString() + "' is a reserved Vespa binding and " +
                                                           "cannot be used by handler: " + newHandler.getComponentId());
            }
        }
    }

    private void removeExistingServerBinding(BindingPattern binding, Handler newHandler, DeployLogger log) {
        for (var handler : cluster.getHandlers()) {
            for (BindingPattern serverBinding : handler.getServerBindings()) {
                if (serverBinding.hasSamePattern(binding)) {
                    handler.removeServerBinding(serverBinding);
                    log.logApplicationPackage(INFO, "Binding '" + binding.patternString() + "' was already in use by handler '" +
                            handler.getComponentId() + "', but will now be taken over by handler: " + newHandler.getComponentId());

                }
            }
        }
    }

}
