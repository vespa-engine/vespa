// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import ai.vespa.validation.Validation;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.Affinity;
import com.yahoo.vespa.model.Client;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.builder.UserConfigBuilder;
import com.yahoo.vespa.model.builder.VespaModelBuilder;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.search.SearchCluster;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Builds Vespa model components using the w3c dom api
 *
 * @author vegardh
 */
public class VespaDomBuilder extends VespaModelBuilder {

    public static final String JVM_OPTIONS = "jvm-options";
    public static final String OPTIONS = "options";
    public static final String JVM_GC_OPTIONS = "jvm-gc-options";
    public static final String GC_OPTIONS = "gc-options";
    public static final String PRELOAD_ATTRIB_NAME = "preload";        // Intended for vespa engineers
    public static final String MMAP_NOCORE_LIMIT = "mmap-core-limit";  // Intended for vespa engineers
    public static final String CORE_ON_OOM = "core-on-oom";            // Intended for vespa engineers
    public static final String NO_VESPAMALLOC = "no-vespamalloc";      // Intended for vespa engineers
    public static final String VESPAMALLOC = "vespamalloc";            // Intended for vespa engineers
    public static final String VESPAMALLOC_DEBUG = "vespamalloc-debug"; // Intended for vespa engineers
    public static final String VESPAMALLOC_DEBUG_STACKTRACE = "vespamalloc-debug-stacktrace"; // Intended for vespa engineers
    public static final String CPU_SOCKET_AFFINITY_ATTRIB_NAME = "cpu-socket-affinity";
    public static final String Allocated_MEMORY_ATTRIB_NAME = "allocated-memory";

    private static final String CPU_SOCKET_ATTRIB_NAME = "cpu-socket";
    private static final Pattern clusterPattern = Pattern.compile("([a-z0-9]|[a-z0-9][a-z0-9_-]{0,61}[a-z0-9])");

    public static final Logger log = Logger.getLogger(VespaDomBuilder.class.getPackage().toString());


    @Override
    public ApplicationConfigProducerRoot getRoot(String name, DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent) {
        try {
            return new DomRootBuilder(name).
                    build(deployState, parent, XmlHelper.getDocument(deployState.getApplicationPackage().getServices(), "services.xml")
                                                        .getDocumentElement());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * @param spec The element containing the xml specification for this Service.
     * @return the user's desired port, which is retrieved from the xml spec.
     */
    public static int getXmlWantedPort(Element spec) {
        return getXmlIntegerAttribute(spec, "baseport");
    }

    /**
     * Base class for builders of producers using DOM. The purpose is to always
     * include hostalias, baseport and user config overrides generically.
     *
     * @param <T> the type of producer to build
     * @param <P> the child type held by the parent of the new producer, usually AnyConfigProducer
     */
    public static abstract class DomConfigProducerBuilder<T extends AnyConfigProducer, P extends AnyConfigProducer>
    {

        // TODO: find good way to provide access to app package
        public final T build(DeployState deployState, TreeConfigProducer<P> ancestor, Element producerSpec) {
            T t = doBuild(deployState, ancestor, producerSpec);

            if (t instanceof AbstractService) {
                initializeService((AbstractService)t, deployState, ancestor.hostSystem(), producerSpec);
            } else {
                initializeProducer(t, deployState, producerSpec);
            }

            return t;
        }

        protected abstract T doBuild(DeployState deployState, TreeConfigProducer<P> ancestor, Element producerSpec);

        private void initializeProducer(AnyConfigProducer child, DeployState deployState, Element producerSpec) {
            UserConfigRepo userConfigs = UserConfigBuilder.build(producerSpec, deployState, deployState.getDeployLogger());
            // TODO: must be made to work:
            //userConfigs.applyWarnings(child);
            log.log(Level.FINE, () -> "Adding user configs " + userConfigs + " for " + producerSpec);
            child.mergeUserConfigs(userConfigs);
        }

        private void initializeService(AbstractService t, DeployState deployState,
                                       HostSystem hostSystem, Element producerSpec) {
            initializeProducer(t, deployState, producerSpec);
            if (producerSpec != null) {
                if (producerSpec.hasAttribute(JVM_OPTIONS)) {
                    t.appendJvmOptions(producerSpec.getAttribute(JVM_OPTIONS));
                }
                if (producerSpec.hasAttribute(PRELOAD_ATTRIB_NAME)) {
                    t.setPreLoad(producerSpec.getAttribute(PRELOAD_ATTRIB_NAME));
                }
                if (producerSpec.hasAttribute(MMAP_NOCORE_LIMIT)) {
                    t.setMMapNoCoreLimit(Long.parseLong(producerSpec.getAttribute(MMAP_NOCORE_LIMIT)));
                }
                if (producerSpec.hasAttribute(CORE_ON_OOM)) {
                    t.setCoreOnOOM(Boolean.parseBoolean(producerSpec.getAttribute(CORE_ON_OOM)));
                }
                if (producerSpec.hasAttribute(NO_VESPAMALLOC)) {
                    t.setNoVespaMalloc(producerSpec.getAttribute(NO_VESPAMALLOC));
                }
                if (producerSpec.hasAttribute(VESPAMALLOC)) {
                    t.setVespaMalloc(producerSpec.getAttribute(VESPAMALLOC));
                }
                if (producerSpec.hasAttribute(VESPAMALLOC_DEBUG)) {
                    t.setVespaMallocDebug(producerSpec.getAttribute(VESPAMALLOC_DEBUG));
                }
                if (producerSpec.hasAttribute(VESPAMALLOC_DEBUG_STACKTRACE)) {
                    t.setVespaMallocDebugStackTrace(producerSpec.getAttribute(VESPAMALLOC_DEBUG_STACKTRACE));
                }
                if (producerSpec.hasAttribute(CPU_SOCKET_ATTRIB_NAME)) {
                    t.setAffinity(new Affinity.Builder().cpuSocket(Integer.parseInt(producerSpec.getAttribute(CPU_SOCKET_ATTRIB_NAME))).build());
                }
                int port = getXmlWantedPort(producerSpec);
                if (port > 0) {
                    t.setBasePort(port);
                }
                allocateHost(t, hostSystem, producerSpec);
            }
            // This depends on which constructor in AbstractService is used, but the best way
            // is to let this method do initialize.
            if (!t.isInitialized()) {
                t.initService(deployState);
            }
        }

        /**
         * Allocates a host to the service using host file or create service spec for provisioner to use later
         * Pre-condition: producerSpec is non-null
         * 
         * @param service the service to allocate a host for
         * @param hostSystem a {@link HostSystem}
         * @param producerSpec xml element for the service
         */
        private void allocateHost(AbstractService service, HostSystem hostSystem, Element producerSpec) {
            // TODO store service on something else than HostSystem, to not make that overloaded
            service.setHostResource(hostSystem.getHost(producerSpec.getAttribute("hostalias")));
        }
    }

    // helper in the usual case where P is AnyConfigProducer
    public static abstract class DomConfigProducerBuilderBase<T extends AnyConfigProducer>
        extends DomConfigProducerBuilder<T, AnyConfigProducer>
    {}

    /**
     * The SimpleConfigProducer is the producer for elements such as container.
     * Must support overrides for that too, hence this builder
     *
     * @author vegardh
     */
    static class DomSimpleConfigProducerBuilder extends DomConfigProducerBuilderBase<SimpleConfigProducer<AnyConfigProducer>> {

        private final String configId;

        DomSimpleConfigProducerBuilder(String configId) {
            this.configId = configId;
        }

        @Override
        protected SimpleConfigProducer<AnyConfigProducer> doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent,
                                                  Element producerSpec) {
            return new SimpleConfigProducer<>(parent, configId);
        }
    }

    public static class DomRootBuilder extends VespaDomBuilder.DomConfigProducerBuilderBase<ApplicationConfigProducerRoot> {
        private final String name;

        /**
         * @param name The name of the Vespa to create. Usually 'root' when there is only one.
         */
        DomRootBuilder(String name) {
            this.name = name;
        }

        @Override
        protected ApplicationConfigProducerRoot doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element producerSpec) {
            ApplicationConfigProducerRoot root = new ApplicationConfigProducerRoot(parent,
                                                                                   name,
                                                                                   deployState.getDocumentModel(),
                                                                                   deployState.getVespaVersion(),
                                                                                   deployState.getProperties().applicationId());
            root.setHostSystem(new HostSystem(root, "hosts", deployState.getProvisioner(),
                                              deployState.getDeployLogger(),
                                              deployState.isHosted()));
            new Client(root);
            return root;
        }
    }

    /**
     * Gets an integer attribute value from a service's spec
     *
     * @param spec          XML element
     * @param attributeName nam of attribute to get value from
     * @return value of attribute, or 0 if it does not exist or is empty
     */
    private static int getXmlIntegerAttribute(Element spec, String attributeName) {
        String value = (spec == null) ? null : spec.getAttribute(attributeName);
        if (value == null || value.equals("")) {
            return 0;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException
                        ("Illegal format for attribute '" + attributeName + "' at node " +
                                XML.getNodePath(spec, " > ") + ", must be an integer", e);
            }
        }
    }

    /**
     * Processing that requires access across different plugins
     *
     * @param root root config producer
     * @param configModelRepo a {@link ConfigModelRepo}
     */
    public void postProc(DeployState deployState, TreeConfigProducer<AnyConfigProducer> root, ConfigModelRepo configModelRepo) {
        setContentSearchClusterIndexes(configModelRepo);
        createDocprocMBusServersAndClients(configModelRepo);
        if (deployState.isHosted()) validateContainerClusterIds(configModelRepo);
    }

    private void createDocprocMBusServersAndClients(ConfigModelRepo pc) {
        for (ContainerCluster<?> cluster: ContainerModel.containerClusters(pc)) {
            addServerAndClientsForChains(cluster.getDocproc());
        }
    }

    private void addServerAndClientsForChains(ContainerDocproc docproc) {
        if (docproc != null)
            docproc.getChains().addServersAndClientsForChains();
    }

    private void validateContainerClusterIds(ConfigModelRepo pc) {
        Map<String, String> normalizedClusterIds = new LinkedHashMap<>();
        for (ContainerCluster<?> cluster: ContainerModel.containerClusters(pc)) {
            if (cluster.getHttp() == null) continue;
            String name = cluster.getName();
            Validation.requireMatch(name, "container cluster name", clusterPattern);
            String clashing = normalizedClusterIds.put(name.replaceAll("_", "-"), name);
            if (clashing != null) throw new IllegalArgumentException("container clusters '" + clashing + "' and '" + name +
                                                                     "' have clashing endpoint names, when '_' is replaced " +
                                                                     "with '-' to form valid domain names");
        }
    }

    /**
     * For some reason, search clusters need to be enumerated.
     * @param configModelRepo a {@link ConfigModelRepo}
     */
    private void setContentSearchClusterIndexes(ConfigModelRepo configModelRepo) {
        int index = 0;
        for (SearchCluster sc : Content.getSearchClusters(configModelRepo)) {
            sc.setClusterIndex(index++);
        }
    }

}
