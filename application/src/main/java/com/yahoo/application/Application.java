// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.google.common.annotations.Beta;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.impl.StandaloneContainerRunner;
import com.yahoo.application.content.ContentCluster;
import com.yahoo.config.*;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.Renderer;
import com.yahoo.text.StringUtilities;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.model.VespaModel;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Contains one or more containers built from services.xml.
 * Other services present in the services.xml file might be mocked in future versions.
 *
 * Currently, only a single top level JDisc Container is allowed. Other clusters are ignored.
 *
 * @author Tony Vaagenes
 */
@Beta
public final class Application implements AutoCloseable {

    /**
     * This system property is set to "true" upon creation of an Application.
     * This is useful for components which are created by dependendy injection which needs to modify
     * their behavior to function without reliance on any processes outside the JVM.
     */
    public static final String vespaLocalProperty = "vespa.local";

    private final JDisc container;
    private final List<ContentCluster> contentClusters;
    private final Path path;
    private final boolean deletePathWhenClosing;

    // For internal use only
    Application(Path path, Networking networking, boolean deletePathWhenClosing) {
        System.setProperty(vespaLocalProperty, "true");
        this.path = path;
        this.deletePathWhenClosing = deletePathWhenClosing;
        contentClusters = ContentCluster.fromPath(path);
        container = JDisc.fromPath(path, networking, createVespaModel().configModelRepo());
    }

    @Beta
    public static Application fromBuilder(Builder builder) throws Exception {
        return builder.build();
    }

    /**
     * Factory method to create an Application from an XML String. Note that any components that are referenced in
     * the XML must be present on the classpath. To deploy OSGi bundles in memory,
     * use {@link Application#fromApplicationPackage(Path, Networking)}.
     *
     * @param xml the XML configuration to use
     * @return a new JDisc instance
     */
    public static Application fromServicesXml(String xml, Networking networking) {
        Path applicationDir = StandaloneContainerRunner.createApplicationPackage(xml);
        return new Application(applicationDir, networking, true);
    }

    /**
     * Factory method to create an Application from an application package.
     * This method allows deploying OSGi bundles(contained in the components subdirectory).
     * All the OSGi bundles will share the same class loader.
     *
     * @param path the reference to the application package to use
     * @return a new JDisc instance
     */
    public static Application fromApplicationPackage(Path path, Networking networking) {
        return new Application(path, networking, false);
    }

    /**
     * Factory method to create an Application from an application package.
     * This method allows deploying OSGi bundles(contained in the components subdirectory).
     * All the OSGi bundles will share the same class loader.
     *
     * @param file the reference to the application package to use
     * @return a new JDisc instance
     */
    public static Application fromApplicationPackage(File file, Networking networking) {
        return fromApplicationPackage(file.toPath(), networking);
    }

    private VespaModel createVespaModel() {
        try {
            DeployState deployState = new DeployState.Builder()
                    .applicationPackage(FilesApplicationPackage.fromFile(path.toFile(), 
                                                                         /* Include source files */ true))
                    .deployLogger((level, s) -> { })
                    .build(true);
            return new VespaModel(new NullConfigModelRegistry(), deployState);
        } catch (IOException | SAXException e) {
            throw new IllegalArgumentException("Error creating application from '" + path + "'", e);
        }
    }

    /**
     * @param id from the jdisc element in services xml. Default id in services.xml is "jdisc"
     */
    public JDisc getJDisc(String id) {
        return container;
    }

    /**
     * Shuts down all services.
     */
    @Override
    public void close() {
        container.close();
        if (deletePathWhenClosing)
            IOUtils.recursiveDeleteDir(path.toFile());
    }

    /**
     * A wrapper around ApplicationBuilder that generates a services.xml
     */
    @Beta
    public static class Builder {
        private static final ThreadLocal<Random> random = new ThreadLocal<>();
        private static final String DEFAULT_CHAIN = "default";

        private final Map<String, Container> containers = new LinkedHashMap<>();
        private final Path path;
        private Networking networking = Networking.disable;

        public Builder() throws IOException {
            this.path = makeTempDir("app", "standalone").toPath();
        }

        public Builder container(String id, Container container) {
            if (containers.size() > 0) {
                throw new RuntimeException("Only a single JDisc container is currently supported.");
            }
            containers.put(id, container);
            return this;
        }

        /**
         * Create a temporary directory using @{link File.createTempFile()}, but creating
         * a directory instead of a file.
         *
         * @param prefix directory prefix
         * @param suffix directory suffix
         * @return The created directory
         * @throws IOException if the temporary directory could not be created
         */
        private static File makeTempDir(String prefix, String suffix) throws IOException {
            File tmpDir = File.createTempFile(prefix, suffix, getTempDir());
            if (!tmpDir.delete()) {
                throw new RuntimeException("Could not delete temp directory: " + tmpDir);
            }
            if (!tmpDir.mkdirs()) {
                throw new RuntimeException("Could not create temp directory: " + tmpDir);
            }
            return tmpDir;
        }

        /**
         * Get the temporary directory
         *
         * @return The temporary directory File object
         */
        private static File getTempDir() {
            String rootPath = getResourceFile("/");
            String tmpPath = rootPath + "/tmp/";
            File tmpDir = new File(tmpPath);
            if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                if (!tmpDir.exists()) { // possible race condition may cause mkdirs() to fail, check a second time before failing
                    throw new RuntimeException("Could not create temp dir: " + tmpDir.getAbsolutePath());
                }
            }
            if (!tmpDir.isDirectory()) {
                throw new RuntimeException("Temp dir path is not a directory: " + tmpDir.getAbsolutePath());
            }
            return tmpDir;
        }

        /**
         * Get the file name (path) of a resource or fail if it can not be found
         *
         * @param resource Name of desired resource
         * @return Path of resource
         */
        private static String getResourceFile(String resource) {
            URL resourceUrl = Application.class.getResource(resource);
            if (resourceUrl == null || resourceUrl.getFile() == null || resourceUrl.getFile().isEmpty()) {
                throw new RuntimeException("Could not access resource: " + resource);
            }

            return resourceUrl.getFile();
        }

        // copy from com.yahoo.application.ApplicationBuilder
        private void createFile(final Path path, final String content) throws IOException {
            Files.createDirectories(path.getParent());
            Files.write(path, Utf8.toBytes(content));
        }

        /**
         * @return a random number between 2000 and 62000
         */
        private static int getRandomPort() {
            Random r = random.get();
            if (r == null) {
                r = new Random(System.currentTimeMillis());
                random.set(r);
            }
            return r.nextInt(60000) + 2000;
        }

        /**
         * @param name             name of document type (search definition)
         * @param searchDefinition add this search definition to the application
         * @return builder
         * @throws java.io.IOException e.g.if file not found
         */
        public Builder documentType(final String name, final String searchDefinition) throws IOException {
            Path path = nestedResource(ApplicationPackage.SEARCH_DEFINITIONS_DIR, name, ApplicationPackage.SD_NAME_SUFFIX);
            createFile(path, searchDefinition);
            return this;
        }

        public Builder expressionInclude(final String name, final String searchDefinition) throws IOException {
            Path path = nestedResource(ApplicationPackage.SEARCH_DEFINITIONS_DIR, name, ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX);
            createFile(path, searchDefinition);
            return this;
        }

        /**
         * @param name                  name of rank expression
         * @param rankExpressionContent add this rank expression to the application
         * @return builder
         * @throws java.io.IOException e.g.if file not found
         */
        public Builder rankExpression(final String name, final String rankExpressionContent) throws IOException {
            Path path = nestedResource(ApplicationPackage.SEARCH_DEFINITIONS_DIR, name, ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX);
            createFile(path, rankExpressionContent);
            return this;
        }

        /**
         * @param name         name of query profile
         * @param queryProfile add this queyr profile to the application
         * @return builder
         * @throws java.io.IOException e.g.if file not found
         */
        public Builder queryProfile(final String name, final String queryProfile) throws IOException {
            Path path = nestedResource(ApplicationPackage.QUERY_PROFILES_DIR, name, ".xml");
            createFile(path, queryProfile);
            return this;
        }

        /**
         * @param name             name of query profile type
         * @param queryProfileType add this query profile type to the application
         * @return builder
         * @throws java.io.IOException e.g.if file not found
         */
        public Builder queryProfileType(final String name, final String queryProfileType) throws IOException {
            Path path = nestedResource(ApplicationPackage.QUERY_PROFILE_TYPES_DIR, name, ".xml");
            createFile(path, queryProfileType);
            return this;
        }

        // copy from com.yahoo.application.ApplicationBuilder
        private Path nestedResource(final com.yahoo.path.Path nestedPath, final String name, final String fileType) {
            String nameWithoutSuffix = StringUtilities.stripSuffix(name, fileType);
            return path.resolve(nestedPath.getRelative()).resolve(nameWithoutSuffix + fileType);
        }

        /**
         * @param networking enable or disable networking (disabled by default)
         * @return builder
         */
        public Builder networking(final Networking networking) {
            this.networking = networking;
            return this;
        }

        // generate the services xml and load the container
        private Application build() throws Exception {
            Application app = null;
            Exception exception = null;

            // if we get a bind exception, then retry a few times (may conflict with parallel test runs)
            for (int i = 0; i < 5; i++) {
                try {
                    generateXml();
                    app = new Application(path, networking, true);
                    break;
                } catch (Error e) { // the container thinks this is really serious, in this case is it not in the cause is a BindException
                    // catch bind error and reset container
                    if (e.getCause() != null && e.getCause().getCause() != null && e.getCause().getCause() instanceof BindException) {
                        exception = (Exception) e.getCause().getCause();
                        com.yahoo.container.Container.resetInstance(); // this is needed to be able to recreate the container from config again
                    } else {
                        throw new Exception(e.getCause());
                    }
                }
            }

            if (app == null) {
                throw exception;
            }
            return app;
        }

        private void generateXml() throws Exception {
            try (PrintWriter xml = new PrintWriter(Files.newOutputStream(path.resolve("services.xml")))) {
                xml.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
                //xml.println("<services version=\"1.0\">");
                for (Map.Entry<String, Container> entry : containers.entrySet()) {
                    entry.getValue().build(xml, entry.getKey(), (networking == Networking.enable ? getRandomPort() : -1));
                }
                //xml.println("</services>");
            }
        }

        public static class Container {
            private final Map<String, List<ComponentItem<? extends DocumentProcessor>>> docprocs = new LinkedHashMap<>();
            private final Map<String, List<ComponentItem<? extends Searcher>>> searchers = new LinkedHashMap<>();
            private final List<ComponentItem<? extends Renderer>> renderers = new ArrayList<>();
            private final List<ComponentItem<? extends RequestHandler>> handlers = new ArrayList<>();
            private final List<ComponentItem<? extends ClientProvider>> clients = new ArrayList<>();
            private final List<ComponentItem<? extends ServerProvider>> servers = new ArrayList<>();
            private final List<ComponentItem<?>> components = new ArrayList<>();
            private final List<ConfigInstance> configs = new ArrayList<>();
            private boolean enableSearch = false;

            private static class ComponentItem<T> {
                private String id;
                private Class<? extends T> component;
                private List<ConfigInstance> configs = new ArrayList<>();

                public ComponentItem(String id, Class<? extends T> component, ConfigInstance... configs) {
                    this.id = id;
                    this.component = component;
                    if (configs != null) {
                        Collections.addAll(this.configs, configs);
                    }
                }
            }

            /**
             * @param docproc add this docproc to the default document processing chain
             * @return builder
             */
            public Container documentProcessor(final Class<? extends DocumentProcessor> docproc) {
                return documentProcessor(DEFAULT_CHAIN, docproc);
            }

            /**
             * @param chainName chain name to add docproc
             * @param docproc   add this docproc to the document processing chain
             * @param configs   local docproc configs
             * @return builder
             */
            public Container documentProcessor(final String chainName, final Class<? extends DocumentProcessor> docproc, ConfigInstance... configs) {
                return documentProcessor(docproc.getName(), chainName, docproc, configs);
            }

            /**
             * @param id        component id
             * @param chainName chain name to add docproc
             * @param docproc   add this docproc to the document processing chain
             * @param configs   local docproc configs
             * @return builder
             */
            public Container documentProcessor(String id, final String chainName, final Class<? extends DocumentProcessor> docproc, ConfigInstance... configs) {
                List<ComponentItem<? extends DocumentProcessor>> chain = docprocs.get(chainName);
                if (chain == null) {
                    chain = new ArrayList<>();
                    docprocs.put(chainName, chain);
                }
                chain.add(new ComponentItem<>(id, docproc, configs));
                return this;
            }

            /**
             * @param enableSearch if true, enable search even without any searchers defined
             * @return builder
             */
            public Container search(boolean enableSearch) {
                this.enableSearch = enableSearch;
                return this;
            }

            /**
             * @param searcher add this searcher to the default search chain
             * @return builder
             */
            public Container searcher(final Class<? extends Searcher> searcher) {
                return searcher(DEFAULT_CHAIN, searcher);
            }

            /**
             * @param chainName chain name to add searcher
             * @param searcher  add this searcher to the search chain
             * @param configs   local searcher configs
             * @return builder
             */
            public Container searcher(final String chainName, final Class<? extends Searcher> searcher, ConfigInstance... configs) {
                return searcher(searcher.getName(), chainName, searcher, configs);
            }

            /**
             * @param id        component id
             * @param chainName chain name to add searcher
             * @param searcher  add this searcher to the search chain
             * @param configs   local searcher configs
             * @return builder
             */
            public Container searcher(String id, final String chainName, final Class<? extends Searcher> searcher, ConfigInstance... configs) {
                List<ComponentItem<? extends Searcher>> chain = searchers.get(chainName);
                if (chain == null) {
                    chain = new ArrayList<>();
                    searchers.put(chainName, chain);
                }
                chain.add(new ComponentItem<>(id, searcher, configs));
                return this;
            }

            /**
             * @param id       component id, enable template with ?format=id or ?presentation.format=id
             * @param renderer add this renderer
             * @param configs  local renderer configs
             * @return builder
             */
            public Container renderer(String id, final Class<? extends Renderer> renderer, ConfigInstance... configs) {
                renderers.add(new ComponentItem<>(id, renderer, configs));
                return this;
            }

            /**
             * @param binding binding string
             * @param handler the handler class
             * @return builder
             */
            public Container handler(final String binding, final Class<? extends RequestHandler> handler) {
                handlers.add(new ComponentItem<>(binding, handler));
                return this;
            }

            /**
             * @param binding binding string
             * @param client  the client class
             * @return builder
             */
            public Container client(final String binding, final Class<? extends ClientProvider> client) {
                clients.add(new ComponentItem<>(binding, client));
                return this;
            }

            /**
             * @param id     server compoent id
             * @param server the server class
             * @return builder
             */
            public Container server(final String id, final Class<? extends ServerProvider> server) {
                servers.add(new ComponentItem<>(id, server));
                return this;
            }

            /**
             * @param component make this component available to the container
             * @return builder
             */
            public Container component(final Class<?> component) {
                return component(component.getName(), component, (ConfigInstance) null);
            }

            /**
             * @param component make this component available to the container
             * @return builder
             */
            public Container component(String id, final Class<?> component, ConfigInstance... configs) {
                components.add(new ComponentItem<>(id, component, configs));
                return this;
            }

            /**
             * @param config add this config to the application
             * @return builder
             */
            public Container config(final ConfigInstance config) {
                configs.add(config);
                return this;
            }

            // generate services.xml based on this builder
            private void build(PrintWriter xml, String id, int port) throws Exception {
                xml.println("<jdisc version=\"1.0\" id=\"" + id + "\">");

                if (port > 0) {
                    xml.println("<http>");
                    xml.println("<server id=\"http\" port=\"" + port + "\" />");
                    xml.println("</http>");
                }

                for (ComponentItem<? extends RequestHandler> entry : handlers) {
                    xml.println("<handler id=\"" + entry.component.getName() + "\">");
                    xml.println("<binding>" + entry.id + "</binding>");
                    xml.println("</handler>");
                }

                for (ComponentItem<? extends ClientProvider> entry : clients) {
                    xml.println("<client id=\"" + entry.component.getName() + "\">");
                    xml.println("<binding>" + entry.id + "</binding>");
                    xml.println("</client>");
                }

                for (ComponentItem<? extends ServerProvider> server : servers) {
                    generateComponent(xml, server, "server");
                }

                // container scoped configs
                for (ConfigInstance config : configs) {
                    generateConfig(xml, config);
                }

                for (ComponentItem<?> component : components) {
                    generateComponent(xml, component, "component");
                }

                if (!docprocs.isEmpty()) {
                    xml.println("<document-processing>");
                    for (Map.Entry<String, List<ComponentItem<? extends DocumentProcessor>>> entry : docprocs.entrySet()) {
                        xml.println("<chain id=\"" + entry.getKey() + "\">");
                        for (ComponentItem<? extends DocumentProcessor> docproc : entry.getValue()) {
                            generateComponent(xml, docproc, "documentprocessor");
                        }
                        xml.println("</chain>");
                    }
                    xml.println("</document-processing>");
                }

                if (enableSearch || !searchers.isEmpty() || !renderers.isEmpty()) {
                    xml.println("<search>");
                    for (Map.Entry<String, List<ComponentItem<? extends Searcher>>> entry : searchers.entrySet()) {
                        xml.println("<chain id=\"" + entry.getKey() + "\">");
                        for (ComponentItem<? extends Searcher> searcher : entry.getValue()) {
                            generateComponent(xml, searcher, "searcher");
                        }
                        xml.println("</chain>");
                    }
                    for (ComponentItem<? extends Renderer> renderer : renderers) {
                        generateComponent(xml, renderer, "renderer");
                    }
                    xml.println("</search>");
                }

                xml.println("</jdisc>");
            }

            private void generateComponent(PrintWriter xml, ComponentItem<?> componentItem, String elementName) throws Exception {
                xml.print("<" + elementName + " id=\"" + componentItem.id + "\" class=\"" + componentItem.component.getName() + "\"");
                if (componentItem.configs.isEmpty() || (!componentItem.configs.isEmpty() && componentItem.configs.get(0) == null)) {
                    xml.println(" />");
                } else {
                    xml.println(">");
                    for (ConfigInstance config : componentItem.configs) {
                        generateConfig(xml, config);
                    }
                    xml.println("</" + elementName + ">");
                }
            }

            // uses reflection to generate XML from a config object
            private void generateConfig(PrintWriter xml, ConfigInstance config) throws Exception {
                Field nameField = config.getClass().getField("CONFIG_DEF_NAME");
                String name = (String) nameField.get(config);
                Field namespaceField = config.getClass().getField("CONFIG_DEF_NAMESPACE");
                String namespace = (String) namespaceField.get(config);

                xml.println("<config name=\"" + namespace + "." + name + "\">");
                generateConfigNode(xml, config);
                xml.println("</config>");
            }

            private void generateConfigNode(PrintWriter xml, InnerNode node) throws Exception {
                // print all leaf nodes as config values
                Field[] fields = node.getClass().getDeclaredFields();
                for (Field field : fields) {
                    generateConfigField(xml, node, field);
                }
            }

            private void generateConfigField(PrintWriter xml, InnerNode node, Field field) throws Exception {
                field.setAccessible(true);
                if (LeafNode.class.isAssignableFrom(field.getType())) {
                    LeafNode<?> value = (LeafNode<?>) field.get(node);
                    if (value.value() != null) {
                        xml.print("<" + field.getName());
                        String v = value.getValue();
                        if (v.isEmpty()) {
                            xml.println(" />");
                        } else {
                            xml.println(">" + v + "</" + field.getName() + ">");
                        }
                    }
                } else if (InnerNode.class.isAssignableFrom(field.getType())) {
                    xml.println("<" + field.getName() + ">");
                    generateConfigNode(xml, (InnerNode) field.get(node));
                    xml.println("</" + field.getName() + ">");
                } else if (Map.class.isAssignableFrom(field.getType())) {
                    Map<?, ?> map = (Map<?, ?>) field.get(node);
                    if (!map.isEmpty()) {
                        xml.println("<" + field.getName() + ">");
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getValue() instanceof InnerNode) {
                                xml.println("<item key=\"" + entry.getKey() + "\">");
                                generateConfigNode(xml, (InnerNode) entry.getValue());
                                xml.println("</item>");
                            } else if (entry.getValue() instanceof LeafNode) {
                                xml.println("<item key=\"" + entry.getKey() + "\">" + ((LeafNode<?>) entry.getValue()).getValue() + "</item>");
                            }
                        }
                        xml.println("</" + field.getName() + ">");
                    }
                } else if (InnerNodeVector.class.isAssignableFrom(field.getType())) {
                    InnerNodeVector<? extends InnerNode> vector = (InnerNodeVector<? extends InnerNode>) field.get(node);
                    if (!vector.isEmpty()) {
                        xml.println("<" + field.getName() + ">");
                        for (InnerNode innerNode : vector) {
                            xml.println("<item>");
                            generateConfigNode(xml, innerNode);
                            xml.println("</item>");
                        }
                        xml.println("</" + field.getName() + ">");
                    }
                } else if (LeafNodeVector.class.isAssignableFrom(field.getType())) {
                    LeafNodeVector<?, ? extends LeafNode<?>> vector = (LeafNodeVector<?, ? extends LeafNode<?>>) field.get(node);
                    if (!vector.isEmpty()) {
                        xml.println("<" + field.getName() + ">");
                        for (LeafNode<?> item : vector) {
                            xml.println("<item>" + item.getValue() + "</item>");
                        }
                        xml.println("</" + field.getName() + ">");
                    }
                }
            }
        }
    }

}
