// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.di.config.JerseyBundlesConfig;
import com.yahoo.container.di.config.JerseyInjectionConfig;
import com.yahoo.container.di.config.JerseyInjectionConfig.Inject;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerClusterImpl;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * @author gjoranv
 */
public class RestApiContext extends SimpleComponent implements
        JerseyBundlesConfig.Producer,
        JerseyInjectionConfig.Producer
{
    private static final Logger log = Logger.getLogger(RestApi.class.getName());
    public static final String CONTAINER_CLASS = "com.yahoo.container.di.config.RestApiContext";

    private final List<BundleInfo> bundles = new ArrayList<>();

    // class name -> componentId
    private final Map<String, String> injectComponentForClass = new LinkedHashMap<>();

    private final String bindingPath;

    @Nullable
    private ContainerClusterImpl containerCluster;

    public RestApiContext(AbstractConfigProducer<?> ancestor, String bindingPath) {
        super(componentModel(bindingPath));
        this.bindingPath = bindingPath;

        if (ancestor instanceof ContainerClusterImpl)
            containerCluster = (ContainerClusterImpl)ancestor;

    }

    private static ComponentModel componentModel(String bindingPath) {
        return new ComponentModel(BundleInstantiationSpecification.getFromStrings(
                CONTAINER_CLASS + "-" + RestApi.idFromPath(bindingPath),
                CONTAINER_CLASS,
                null));
    }

    @Override
    public void getConfig(JerseyBundlesConfig.Builder builder) {
        builder.bundles(createBundlesConfig(bundles));
    }

    private List<JerseyBundlesConfig.Bundles.Builder> createBundlesConfig(List<BundleInfo> bundles) {
        List<JerseyBundlesConfig.Bundles.Builder> builders = new ArrayList<>();
        for (BundleInfo b : bundles) {
            builders.add(
                    new JerseyBundlesConfig.Bundles.Builder()
                            .spec(b.spec)
                            .packages(b.getPackagesToScan())
            );
        }
        return builders;
    }

    public void addBundles(Collection<BundleInfo> newBundles) {
        bundles.addAll(newBundles);
    }

    @Override
    public void getConfig(JerseyInjectionConfig.Builder builder) {
        for (Map.Entry<String, String> i : injectComponentForClass.entrySet()) {
            builder.inject(new Inject.Builder()
                                   .forClass(i.getKey())
                                   .instance(i.getValue()));
        }
    }

    @Override
    public void validate() throws Exception {
        super.validate();

        if (bundles.isEmpty())
            log.warning("No bundles in rest-api '" + bindingPath +
                                "' - components will only be loaded from classpath.");
    }

    public void prepare() {
        if (containerCluster == null) return;

        containerCluster.getAllComponents().stream().
                filter(isCycleGeneratingComponent.negate()).
                forEach(this::inject);
    }


    /*
     * Example problem
     *
     * RestApiContext -> ApplicationStatusHandler -> ComponentRegistry<HttpServer> -> JettyHttpServer -> ComponentRegistry<Jersey2Servlet> -> RestApiContext
     */
    private Predicate<Component> isCycleGeneratingComponent = component -> {
        switch (component.getClassId().getName()) {
            case CONTAINER_CLASS:
            case Jersey2Servlet.CLASS:
            case "com.yahoo.jdisc.http.server.jetty.JettyHttpServer":
            case "com.yahoo.container.handler.observability.ApplicationStatusHandler":
                return true;
            default:
                return false;
        }
    };

    public static class BundleInfo {
        // SymbolicName[:Version]
        public final String spec;

        private final List<String> packagesToScan = new ArrayList<>();

        public BundleInfo(String spec) {
            this.spec = spec;
        }

        public List<String> getPackagesToScan() {
            return packagesToScan;
        }

        public void addPackageToScan(String pkg) {
            packagesToScan.add(pkg);
        }
    }

}
