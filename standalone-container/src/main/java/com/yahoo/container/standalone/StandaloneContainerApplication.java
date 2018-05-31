// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.application.provider.StaticConfigDefinitionRepo;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.container.jdisc.ConfiguredApplication;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.application.Application;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.xml.ConfigServerContainerModelBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.collections.CollectionUtil.first;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class StandaloneContainerApplication implements Application {
    public static final String PACKAGE_NAME = "standalone_jdisc_container";
    public static final String APPLICATION_LOCATION_INSTALL_VARIABLE = PACKAGE_NAME + ".app_location";
    public static final String DEPLOYMENT_PROFILE_INSTALL_VARIABLE = PACKAGE_NAME + ".deployment_profile";
    public static final String DISABLE_NETWORKING_ANNOTATION = "JDisc.disableNetworking";
    public static final Named APPLICATION_PATH_NAME = Names.named(APPLICATION_LOCATION_INSTALL_VARIABLE);
    public static final Named CONFIG_MODEL_REPO_NAME = Names.named("ConfigModelRepo");

    private static final String DEFAULT_TMP_BASE_DIR = Defaults.getDefaults().underVespaHome("tmp");
    private static final String TMP_DIR_NAME = "standalone_container";

    private static final StaticConfigDefinitionRepo configDefinitionRepo = new StaticConfigDefinitionRepo();

    private final Injector injector;
    private final Path applicationPath;
    private final LocalFileDb distributedFiles;
    private final ConfigModelRepo configModelRepo;
    private final Networking networkingOption;
    private final VespaModel modelRoot;
    private final Application configuredApplication;
    private final Container container;

    @Inject
    public StandaloneContainerApplication(Injector injector) {
        this.injector = injector;
        ConfiguredApplication.ensureVespaLoggingInitialized();
        this.applicationPath = injectedApplicationPath().orElseGet(this::installApplicationPath);
        this.distributedFiles = new LocalFileDb(applicationPath);
        this.configModelRepo = resolveConfigModelRepo();
        this.networkingOption = resolveNetworkingOption();

        try {
            Pair<VespaModel, Container> tpl = withTempDir(preprocessedApplicationDir -> createContainerModel(applicationPath,
                    distributedFiles, preprocessedApplicationDir, networkingOption, configModelRepo));
            this.modelRoot = tpl.getFirst();
            this.container = tpl.getSecond();
        } catch (RuntimeException r) {
            throw r;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ContainerModel", e);
        }
        this.configuredApplication = createConfiguredApplication(container);
    }

    private ConfigModelRepo resolveConfigModelRepo() {
        try {
            return injector.getInstance(Key.get(ConfigModelRepo.class, CONFIG_MODEL_REPO_NAME));
        } catch (Exception e) {
            return new ConfigModelRepo();
        }
    }

    private Networking resolveNetworkingOption() {
        try {
            Boolean networkingDisable = injector.getInstance(Key.get(Boolean.class, Names.named(DISABLE_NETWORKING_ANNOTATION)));
            if (networkingDisable != null) {
                return networkingDisable ? Networking.disable : Networking.enable;
            }
        } catch (Exception ignored) {
        }
        return Networking.enable;
    }

    private Application createConfiguredApplication(Container container) {
        Injector augmentedInjector = injector.createChildInjector(new AbstractModule() {
            @Override
            public void configure() {
                bind(SubscriberFactory.class).toInstance(new StandaloneSubscriberFactory(modelRoot));
            }
        });

        System.setProperty("config.id", container.getConfigId());
        return augmentedInjector.getInstance(ConfiguredApplication.class);
    }

    private Optional<Path> injectedApplicationPath() {
        try {
            return Optional.ofNullable(injector.getInstance(Key.get(Path.class, APPLICATION_PATH_NAME)));
        } catch (ConfigurationException | ProvisionException ignored) {
        }
        return Optional.empty();
    }

    private Path installApplicationPath() {
        Optional<String> variable = optionalInstallVariable(APPLICATION_LOCATION_INSTALL_VARIABLE);

        return variable.map(Paths::get)
                .orElseThrow(() -> new IllegalStateException("Environment variable not set: " + APPLICATION_LOCATION_INSTALL_VARIABLE));
    }

    @Override
    public void start() {
        try {
            com.yahoo.container.Container.get().setCustomFileAcquirer(distributedFiles);
            configuredApplication.start();
        } catch (Exception e) {
            com.yahoo.container.Container.resetInstance();
            throw e;
        }
    }

    @Override
    public void stop() {
        configuredApplication.stop();
    }

    @Override
    public void destroy() {
        com.yahoo.container.Container.resetInstance();
        configuredApplication.destroy();
    }

    public Container container() {
        return container;
    }

    private interface ThrowingFunction<T, U> {
        U apply(T input) throws Exception;
    }

    private static <T> T withTempDir(ThrowingFunction<File, T> f) throws Exception {
        File tmpDir = createTempDir();
        try {
            return f.apply(tmpDir);
        } finally {
            IOUtils.recursiveDeleteDir(tmpDir);
        }
    }

    private static File createTempDir() {
        Path basePath;
        if (new File(DEFAULT_TMP_BASE_DIR).exists()) {
            basePath = Paths.get(DEFAULT_TMP_BASE_DIR);
        } else {
            basePath = Paths.get(System.getProperty("java.io.tmpdir"));
        }

        try {
            Path tmpDir = Files.createTempDirectory(basePath, TMP_DIR_NAME);
            return tmpDir.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Cannot create temp directory", e);
        }
    }

    private static void validateApplication(ApplicationPackage applicationPackage) {
        try {
            applicationPackage.validateXML();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static ContainerModelBuilder newContainerModelBuilder(Networking networkingOption) {
        Optional<String> profile = optionalInstallVariable(DEPLOYMENT_PROFILE_INSTALL_VARIABLE);
        if (profile.isPresent()) {
            String profileName = profile.get();
            if ("configserver".equals(profileName)) {
                return new ConfigServerContainerModelBuilder(new CloudConfigInstallVariables());
            } else {
                throw new RuntimeException("Invalid deployment profile '" + profileName + "'");
            }
        } else {
            return new ContainerModelBuilder(true, networkingOption);
        }
    }

    static Pair<VespaModel, Container> createContainerModel(Path applicationPath, FileRegistry fileRegistry,
            File preprocessedApplicationDir, Networking networkingOption, ConfigModelRepo configModelRepo) throws Exception {
        DeployLogger logger = new BaseDeployLogger();
        FilesApplicationPackage rawApplicationPackage = new FilesApplicationPackage.Builder(applicationPath.toFile())
                .includeSourceFiles(true).preprocessedDir(preprocessedApplicationDir).build();
        ApplicationPackage applicationPackage = rawApplicationPackage.preprocess(Zone.defaultZone(), logger);
        validateApplication(applicationPackage);
        DeployState deployState = new DeployState.Builder().applicationPackage(applicationPackage).fileRegistry(fileRegistry)
                .deployLogger(logger).configDefinitionRepo(configDefinitionRepo).build(true);

        VespaModel root = VespaModel.createIncomplete(deployState);
        ApplicationConfigProducerRoot vespaRoot = new ApplicationConfigProducerRoot(root, "vespa", deployState.getDocumentModel(),
                deployState.getProperties().vespaVersion(), deployState.getProperties().applicationId());

        Element spec = containerRootElement(applicationPackage);
        ContainerModel containerModel = newContainerModelBuilder(networkingOption).build(deployState, configModelRepo, vespaRoot, spec);
        containerModel.getCluster().prepare();
        initializeContainerModel(containerModel, configModelRepo);
        Container container = first(containerModel.getCluster().getContainers());

        // TODO: Separate out model finalization from the VespaModel constructor,
        // such that the above and below code to finalize the container can be
        // replaced by root.finalize();

        initializeContainer(container, spec);

        root.freezeModelTopology();
        return new Pair<>(root, container);
    }

    private static void initializeContainer(Container container, Element spec) {
        HostResource host = container.getRoot().getHostSystem().getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC);

        container.setBasePort(VespaDomBuilder.getXmlWantedPort(spec));
        container.setHostResource(host);
        container.initService();
    }

    private static Element getJDiscInServices(Element element) {
        List<Element> jDiscElements = new ArrayList<>();
        for (ConfigModelId cid : ContainerModelBuilder.configModelIds) {
            List<Element> children = XML.getChildren(element, cid.getName());
            jDiscElements.addAll(children);
        }
        
        if (jDiscElements.size() == 1) {
            return jDiscElements.get(0);
        } else if (jDiscElements.isEmpty()) {
            throw new RuntimeException("No jdisc element found under services.");
        } else {
            List<String> nameAndId = jDiscElements.stream().map(e -> e.getNodeName() + " id='" + e.getAttribute("id") + "'")
                    .collect(Collectors.toList());
            throw new RuntimeException("Found multiple JDisc elements: " + String.join(", ", nameAndId));
        }
    }

    private static Element containerRootElement(ApplicationPackage applicationPackage) {
        Element element = XmlHelper.getDocument(applicationPackage.getServices()).getDocumentElement();
        String nodeName = element.getNodeName();

        if (ContainerModelBuilder.configModelIds.stream().anyMatch(id -> id.getName().equals(nodeName))) {
            return element;
        } else {
            return getJDiscInServices(element);
        }
    }

    @SuppressWarnings("deprecation") // TODO: what is the not-deprecated way?
    private static void initializeContainerModel(ContainerModel containerModel, ConfigModelRepo configModelRepo) {
        containerModel.initialize(configModelRepo);
    }

    private static Optional<String> optionalInstallVariable(String name) {
        Optional<String> fromEnv = Optional.ofNullable(System.getenv((name.replace(".", "__"))));
        if (fromEnv.isPresent()) {
            return fromEnv;
        }
        return Optional.ofNullable(System.getProperty(name)); // for unit testing
    }
}
