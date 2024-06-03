// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.container.jdisc.ConfiguredApplication;
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private static final String DEFAULT_TMP_BASE_DIR = Defaults.getDefaults().underVespaHome("var/tmp");
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

    @SuppressWarnings("WeakerAccess")
    @Inject
    public StandaloneContainerApplication(Injector injector) {
        this.injector = injector;
        ConfiguredApplication.ensureVespaLoggingInitialized();
        this.applicationPath = injectedApplicationPath().orElseGet(this::installApplicationPath);
        this.distributedFiles = new LocalFileDb(applicationPath);
        this.configModelRepo = resolveConfigModelRepo();
        this.networkingOption = resolveNetworkingOption();

        try {
            Pair<VespaModel, Container> tpl = createContainerModel(applicationPath, distributedFiles, networkingOption, configModelRepo);
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
            com.yahoo.container.Container.get().disableUrlDownloader();
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

    private static void validateApplication(ApplicationPackage applicationPackage) {
        try {
            applicationPackage.validateXML();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static ContainerModelBuilder newContainerModelBuilder(Networking networkingOption) {
        return isConfigServer() ?
                new ConfigServerContainerModelBuilder(new CloudConfigInstallVariables()) :
                new ContainerModelBuilder(true, networkingOption);
    }

    private static boolean isConfigServer() {
        Optional<String> profile = optionalInstallVariable(DEPLOYMENT_PROFILE_INSTALL_VARIABLE);
        if (profile.isPresent()) {
            String profileName = profile.get();
            if (profileName.equals("configserver"))
                return true;
            else
                throw new RuntimeException("Invalid deployment profile '" + profileName + "'");
        }

        return false;
    }

    static Pair<VespaModel, Container> createContainerModel(Path applicationPath, FileRegistry fileRegistry,
            Networking networkingOption, ConfigModelRepo configModelRepo) throws Exception {
        DeployLogger logger = new BaseDeployLogger();
        FilesApplicationPackage rawApplicationPackage =
                new FilesApplicationPackage.Builder(applicationPath.toFile()).includeSourceFiles(true).build();
        ApplicationPackage applicationPackage = rawApplicationPackage.preprocess(getZone(), logger);
        validateApplication(applicationPackage);
        DeployState deployState = createDeployState(applicationPackage, fileRegistry, logger);

        VespaModel root = VespaModel.createIncomplete(deployState);
        ApplicationConfigProducerRoot vespaRoot = new ApplicationConfigProducerRoot(root, "vespa", deployState.getDocumentModel(),
                deployState.getVespaVersion(), deployState.getProperties().applicationId());

        Element spec = containerRootElement(applicationPackage);
        ContainerModel containerModel = newContainerModelBuilder(networkingOption)
                .build(deployState, root, configModelRepo, vespaRoot, spec);
        containerModel.getCluster().prepare(deployState);
        initializeContainerModel(containerModel, configModelRepo);
        Container container = first(containerModel.getCluster().getContainers());

        // TODO: Separate out model finalization from the VespaModel constructor,
        // such that the above and below code to finalize the container can be
        // replaced by root.finalize();

        initializeContainer(deployState, container, spec);

        root.freezeModelTopology();
        return new Pair<>(root, container);
    }

    private static Zone getZone() {
        if (!isConfigServer()) {
            return Zone.defaultZone();
        }
        CloudConfigInstallVariables cloudConfigVariables = new CloudConfigInstallVariables();
        if (!cloudConfigVariables.hostedVespa().orElse(false)) {
            return Zone.defaultZone();
        }
        RegionName region = cloudConfigVariables.region().map(RegionName::from).orElseGet(RegionName::defaultName);
        Environment environment = cloudConfigVariables.environment().map(Environment::from).orElseGet(Environment::defaultEnvironment);
        SystemName system = cloudConfigVariables.system().map(SystemName::from).orElseGet(SystemName::defaultSystem);
        return new Zone(system, environment, region);
    }

    private static DeployState createDeployState(ApplicationPackage applicationPackage, FileRegistry fileRegistry,
                                                 DeployLogger logger) {
        DeployState.Builder builder = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .fileRegistry(fileRegistry)
                .deployLogger(logger)
                .configDefinitionRepo(configDefinitionRepo);

        return builder.build();
    }

    private static void initializeContainer(DeployState deployState, Container container, Element spec) {
        HostResource host = container.getRoot().hostSystem().getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC);

        container.setBasePort(VespaDomBuilder.getXmlWantedPort(spec));
        container.setHostResource(host);
        container.initService(deployState);
    }

    private static Element getContainerElementInServices(Element element) {
        List<Element> containerElements = new ArrayList<>();
        for (ConfigModelId cid : ContainerModelBuilder.configModelIds) {
            List<Element> children = XML.getChildren(element, cid.getName());
            containerElements.addAll(children);
        }
        
        if (containerElements.size() == 1) {
            return containerElements.get(0);
        } else if (containerElements.isEmpty()) {
            throw new RuntimeException("No container element found under services.");
        } else {
            List<String> nameAndId = containerElements.stream().map(e -> e.getNodeName() + " id='" + e.getAttribute("id") + "'")
                    .toList();
            throw new RuntimeException("Found multiple container elements: " + String.join(", ", nameAndId));
        }
    }

    private static Element containerRootElement(ApplicationPackage applicationPackage) {
        Element element = XmlHelper.getDocument(applicationPackage.getServices()).getDocumentElement();
        String nodeName = element.getNodeName();

        if (ContainerModelBuilder.configModelIds.stream().anyMatch(id -> id.getName().equals(nodeName))) {
            return element;
        } else {
            return getContainerElementInServices(element);
        }
    }

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
