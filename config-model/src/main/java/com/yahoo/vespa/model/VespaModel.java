// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigInstance.Builder;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.config.GenericConfig;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.builder.VespaModelBuilder;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.clients.Clients;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileDistributor;
import com.yahoo.vespa.model.generic.service.ServiceCluster;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.utils.internal.ReflectionUtil;
import com.yahoo.yolean.Exceptions;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.config.codegen.ConfiggenUtil.createClassName;
import static com.yahoo.text.StringUtilities.quote;

/**
 * <p>
 * The root ConfigProducer node for all Vespa systems (there is currently only one).
 * The main class for building the Vespa model.
 * </p>
 * The vespa model starts in an unfrozen state, where children can be added freely,
 * but no structure dependent information can be used.
 * When frozen, structure dependent information(such as config id and controller) are
 * made available, but no additional config producers can be added.
 *
 * @author gjoranv
 */
public final class VespaModel extends AbstractConfigProducerRoot implements Serializable, Model {

    private static final long serialVersionUID = 1L;

    public static final Logger log = Logger.getLogger(VespaModel.class.getPackage().toString());
    private ConfigModelRepo configModelRepo = new ConfigModelRepo();
    private final AllocatedHosts allocatedHosts;

    /**
     * The config id for the root config producer
     */
    public static final String ROOT_CONFIGID = "";

    private ApplicationConfigProducerRoot root = null;

    /**
     * Generic service instances - service clusters which have no specific model
     */
    private List<ServiceCluster> serviceClusters = new ArrayList<>();

    private DeployState deployState;

    /** The validation overrides of this. This is never null. */
    private final ValidationOverrides validationOverrides;
    
    private final FileDistributor fileDistributor;

    /** Creates a Vespa Model from internal model types only */
    public VespaModel(ApplicationPackage app) throws IOException, SAXException {
        this(app, new NullConfigModelRegistry());
    }

    /** Creates a Vespa Model from internal model types only */
    public VespaModel(DeployState deployState) throws IOException, SAXException {
        this(new NullConfigModelRegistry(), deployState);
    }

    /**
     * Constructs vespa model using config given in app
     *
     * @param app the application to create a model from
     * @param configModelRegistry a registry of config model "main" classes which may be used
     *        to instantiate config models
     */
    public VespaModel(ApplicationPackage app, ConfigModelRegistry configModelRegistry) throws IOException, SAXException {
        this(configModelRegistry, new DeployState.Builder().applicationPackage(app).build(true));
    }

    /**
     * Constructs vespa model using config given in app
     *
     * @param configModelRegistry a registry of config model "main" classes which may be used
     *        to instantiate config models
     * @param deployState the global deploy state to use for this model.
     */
    public VespaModel(ConfigModelRegistry configModelRegistry, DeployState deployState) throws IOException, SAXException {
        this(configModelRegistry, deployState, true, null);
    }

    private VespaModel(ConfigModelRegistry configModelRegistry, DeployState deployState, boolean complete, FileDistributor fileDistributor) throws IOException, SAXException {
        super("vespamodel");
        this.deployState = deployState;
        this.validationOverrides = deployState.validationOverrides();
        configModelRegistry = new VespaConfigModelRegistry(configModelRegistry);
        VespaModelBuilder builder = new VespaDomBuilder();
        root = builder.getRoot(VespaModel.ROOT_CONFIGID, deployState, this);
        if (complete) { // create a a completed, frozen model
            configModelRepo.readConfigModels(deployState, builder, root, configModelRegistry);
            addServiceClusters(deployState.getApplicationPackage(), builder);
            this.allocatedHosts = AllocatedHosts.withHosts(root.getHostSystem().getHostSpecs()); // must happen after the two lines above
            setupRouting();
            this.fileDistributor = root.getFileDistributionConfigProducer().getFileDistributor();
            getAdmin().addPerHostServices(getHostSystem().getHosts(), deployState);
            freezeModelTopology();
            root.prepare(configModelRepo);
            configModelRepo.prepareConfigModels();
            validateWrapExceptions();
            this.deployState = null;
        }
        else { // create a model with no services instantiated and the given file distributor
            this.allocatedHosts = AllocatedHosts.withHosts(root.getHostSystem().getHostSpecs());
            this.fileDistributor = fileDistributor;
        }
    }

    /** Creates a mutable model with no services instantiated */
    public static VespaModel createIncomplete(DeployState deployState) throws IOException, SAXException {
        return new VespaModel(new NullConfigModelRegistry(), deployState, false, new FileDistributor(deployState.getFileRegistry(), null));
    }

    private void validateWrapExceptions() {
        try {
            validate();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error while validating model:", e);
        }
    }

    /** Adds generic application specific clusters of services */
    private void addServiceClusters(ApplicationPackage app, VespaModelBuilder builder) {
        for (ServiceCluster sc : builder.getClusters(app, this))
            serviceClusters.add(sc);
    }

    private void setupRouting() {
        root.setupRouting(configModelRepo);
    }

    /** Returns the one and only HostSystem of this VespaModel */
    public HostSystem getHostSystem() {
        return root.getHostSystem();
    }

    /** Return a collection of all hostnames used in this application */
    @Override
    public Set<HostInfo> getHosts() {
        return root.getHostSystem().getHosts().stream()
                   .map(HostResource::getHostInfo)
                   .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public FileDistributor getFileDistributor() {
        return fileDistributor;
    }

    @Override
    public Set<FileReference> fileReferences() {
        return fileDistributor.allFilesToSend();
    }

    /** Returns this models Vespa instance */
    public ApplicationConfigProducerRoot getVespa() { return root; }

    @Override
    public boolean allowModelVersionMismatch(Instant now) {
        return validationOverrides.allows(ValidationId.configModelVersionMismatch, now) ||
               validationOverrides.allows(ValidationId.skipOldConfigModels, now); // implies this
    }

    @Override
    public boolean skipOldConfigModels(Instant now) {
        return validationOverrides.allows(ValidationId.skipOldConfigModels, now);
    }

    /**
     * Resolves config of the given type and config id, by first instantiating the correct {@link com.yahoo.config.ConfigInstance.Builder},
     * calling {@link #getConfig(com.yahoo.config.ConfigInstance.Builder, String)}. The default values used will be those of the config
     * types in the model.
     *
     * @param clazz The type of config
     * @param configId The config id
     * @return A config instance of the given type
     */
    public <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> clazz, String configId) {
        try {
            ConfigInstance.Builder builder = newBuilder(clazz);
            getConfig(builder, configId);
            return newConfigInstance(clazz, builder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Populates an instance of configClass with config produced by configProducer.
     */
    public static <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> configClass, ConfigProducer configProducer) {
        try {
            Builder builder = newBuilder(configClass);
            populateConfigBuilder(builder, configProducer);
            return newConfigInstance(configClass, builder);
        } catch (Exception e) {
            throw new RuntimeException("Failed getting config for class " + configClass.getName(), e);
        }
    }

    private static <CONFIGTYPE extends ConfigInstance> CONFIGTYPE newConfigInstance(Class<CONFIGTYPE> configClass, Builder builder)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException {

        Constructor<CONFIGTYPE> constructor = configClass.getConstructor(builder.getClass());
        return constructor.newInstance(builder);
    }

    private static Builder newBuilder(Class<? extends ConfigInstance> configClass) throws ReflectiveOperationException {
        Class builderClazz = configClass.getClassLoader().loadClass(configClass.getName() + "$Builder");
        return (Builder)builderClazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Throw if the config id does not exist in the model.
     *
     * @param configId a config id
     */
    protected void checkId(String configId) {
        if ( ! id2producer.containsKey(configId)) {
            log.log(LogLevel.DEBUG, "Invalid config id: " + configId);
        }
    }

    /**
     * Resolves config for a given config id and populates the given builder with the config.
     *
     * @param builder a configinstance builder
     * @param configId the config id for the config client
     * @return the builder if a producer was found, and it did apply config, null otherwise
     */
    @SuppressWarnings("unchecked")
    @Override
    public ConfigInstance.Builder getConfig(ConfigInstance.Builder builder, String configId) {
        checkId(configId);
        Optional<ConfigProducer> configProducer = getConfigProducer(configId);
        if ( ! configProducer.isPresent()) return null;

        populateConfigBuilder(builder, configProducer.get());
        return builder;
    }

    private static void populateConfigBuilder(Builder builder, ConfigProducer configProducer) {
        boolean found = configProducer.cascadeConfig(builder);
        boolean foundOverride = configProducer.addUserConfig(builder);
        if (logDebug()) {
            log.log(LogLevel.DEBUG, "Trying to get config for " + builder.getClass().getDeclaringClass().getName() +
                    " for config id " + quote(configProducer.getConfigId()) +
                    ", found=" + found + ", foundOverride=" + foundOverride);
        }
    }

    /**
     * Resolve config for a given key and config definition
     *
     * @param configKey The key to resolve.
     * @param targetDef The config definition to use for the schema
     * @return The payload as a list of strings
     */
    @Override
    public ConfigPayload getConfig(ConfigKey configKey, com.yahoo.vespa.config.buildergen.ConfigDefinition targetDef) {
        ConfigBuilder builder = InstanceResolver.resolveToBuilder(configKey, this, targetDef);
        if (builder != null) {
            log.log(LogLevel.DEBUG, () -> "Found builder for " + configKey);
            ConfigPayload payload;
            InnerCNode innerCNode = targetDef != null ?  targetDef.getCNode() : null;
            if (builder instanceof GenericConfig.GenericConfigBuilder) {
                payload = getConfigFromGenericBuilder(builder);
            } else {
                payload = getConfigFromBuilder(configKey, builder, innerCNode);
            }
            return (innerCNode != null) ? payload.applyDefaultsFromDef(innerCNode) : payload;
        }
        return null;
    }

    private ConfigPayload getConfigFromBuilder(ConfigKey configKey, ConfigBuilder builder, InnerCNode targetDef) {
        try {
            ConfigInstance instance = InstanceResolver.resolveToInstance(configKey, builder, targetDef);
            log.log(LogLevel.DEBUG, () -> "getConfigFromBuilder for " + configKey + ",instance=" + instance);
            return ConfigPayload.fromInstance(instance);
        } catch (ConfigurationRuntimeException e) {
            // This can happen in cases where services ask for config that no longer exist before they have been able
            // to reconfigure themselves. This happens for instance whenever jdisc reconfigures itself until
            // ticket 6599572 is fixed. When that happens, consider propagating a full error rather than empty payload
            // back to the client.
            log.log(LogLevel.INFO, "Error resolving instance for key '" + configKey + "', returning empty config: " + Exceptions.toMessageString(e));
            return ConfigPayload.fromBuilder(new ConfigPayloadBuilder());
        }
    }

    private ConfigPayload getConfigFromGenericBuilder(ConfigBuilder builder)  {
        return ((GenericConfig.GenericConfigBuilder) builder).getPayload();
    }

    @Override
    public Set<ConfigKey<?>> allConfigsProduced() {
        Set<ConfigKey<?>> keySet = new LinkedHashSet<>();
        for (ConfigProducer producer : id2producer().values()) {
            keySet.addAll(configsProduced(producer));
        }
        return keySet;
    }

    public ConfigInstance.Builder createBuilder(ConfigDefinitionKey key, ConfigDefinition targetDef) {
        String className = createClassName(key.getName());
        Class<?> clazz;

        final String fullClassName = InstanceResolver.packageName(key) + "." + className;
        final String builderName = fullClassName + "$Builder";
        final String producerName = fullClassName + "$Producer";
        ClassLoader classLoader = getConfigClassLoader(producerName);
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
            if (logDebug()) {
                log.log(LogLevel.DEBUG, "No producer found to get classloader from for " + fullClassName + ". Using default");
            }
        }
        try {
            clazz = classLoader.loadClass(builderName);
        } catch (ClassNotFoundException e) {
            if (logDebug()) {
                log.log(LogLevel.DEBUG, "Tried to load " + builderName + ", not found, trying with generic builder");
            }
            // TODO: Enable config compiler when configserver is using new API.
            // ConfigCompiler compiler = new LazyConfigCompiler(Files.createTempDir());
            // return compiler.compile(targetDef.generateClass()).newInstance();
            return new GenericConfig.GenericConfigBuilder(key, new ConfigPayloadBuilder());
        }
        Object i;
        try {
            i = clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ConfigurationRuntimeException(e);
        }
        if (!(i instanceof ConfigInstance.Builder)) {
            throw new ConfigurationRuntimeException(fullClassName + " is not a ConfigInstance.Builder, can not produce config for the name '" + key.getName() + "'.");
        }
        return (ConfigInstance.Builder) i;
    }

    private static boolean logDebug() {
        return log.isLoggable(LogLevel.DEBUG);
    }

    /**
     * The set of all config ids present
     * @return set of config ids
     */
    public Set<String> allConfigIds() {
        return id2producer.keySet();
    }

    @Override
    public void distributeFiles(FileDistribution fileDistribution) {
        getFileDistributor().sendDeployedFiles(fileDistribution);
    }

    @Override
    public AllocatedHosts allocatedHosts() {
        return allocatedHosts;
    }

    private static Set<ConfigKey<?>> configsProduced(ConfigProducer cp) {
        Set<ConfigKey<?>> ret = ReflectionUtil.configsProducedByInterface(cp.getClass(), cp.getConfigId());
        UserConfigRepo userConfigs = cp.getUserConfigs();
        for (ConfigDefinitionKey userKey : userConfigs.configsProduced()) {
            ret.add(new ConfigKey<>(userKey.getName(), cp.getConfigId(), userKey.getNamespace()));
        }
        return ret;
    }

    @Override
    public DeployState getDeployState() {
        if (deployState == null)
            throw new IllegalStateException("Cannot call getDeployState() once model has been built");
        return deployState;
    }

    /**
     * @return an unmodifiable copy of the set of configIds in this VespaModel.
     */
    public Set<String> getConfigIds() {
        return Collections.unmodifiableSet(id2producer.keySet());
    }

    /**
     * Returns the admin component of the vespamodel.
     *
     * @return Admin
     */
    public Admin getAdmin() {
        return root.getAdmin();
    }

    /**
     * Adds the descendant (at any depth level), so it can be looked up
     * on configId in the Map.
     *
     * @param configId   the id to register with, not necessarily equal to descendant.getConfigId().
     * @param descendant The configProducer descendant to add
     */
    public void addDescendant(String configId, AbstractConfigProducer descendant) {
        if (id2producer.containsKey(configId)) {
            throw new RuntimeException
                    ("Config ID '" + configId + "' cannot be reserved by an instance of class '" +
                            descendant.getClass().getName() +
                            "' since it is already used by an instance of class '" +
                            id2producer.get(configId).getClass().getName() +
                            "'. (This is commonly caused by service/node index " +
                            "collisions in the config.)");
        }
        id2producer.put(configId, descendant);
    }

    /**
     * Writes MODEL.cfg files for all config producers.
     *
     * @param baseDirectory dir to write files to
     */
    public void writeFiles(File baseDirectory) throws IOException {
        super.writeFiles(baseDirectory);
        for (ConfigProducer cp : id2producer.values()) {
            try {
                File destination = new File(baseDirectory, cp.getConfigId().replace("/", File.separator));
                cp.writeFiles(destination);
            } catch (IOException e) {
                throw new IOException(cp.getConfigId() + ": " + e.getMessage());
            }
        }
    }

    public Clients getClients() {
        return configModelRepo.getClients();
    }

    /** Returns all search clusters, both in Search and Content */
    public List<AbstractSearchCluster> getSearchClusters() {
        return Content.getSearchClusters(configModelRepo());
    }

    /** Returns a map of content clusters by ID */
    public Map<String, ContentCluster> getContentClusters() {
        Map<String, ContentCluster> clusters = new LinkedHashMap<>();
        for (Content model : configModelRepo.getModels(Content.class)) {
            clusters.put(model.getId(), model.getCluster());
        }
        return Collections.unmodifiableMap(clusters);
    }

    /** Returns a map of container clusters by ID */
    public Map<String, ContainerCluster> getContainerClusters() {
        Map<String, ContainerCluster> clusters = new LinkedHashMap<>();
        for (ContainerModel model : configModelRepo.getModels(ContainerModel.class)) {
            clusters.put(model.getId(), model.getCluster());
        }
        return Collections.unmodifiableMap(clusters);
    }

    /** Returns the routing config model. This might be null. */
    public Routing getRouting() {
        return configModelRepo.getRouting();
    }

    public FileDistributionConfigProducer getFileDistributionConfigProducer() {
        return root.getFileDistributionConfigProducer();
    }

    /** The clusters of application specific generic services */
    public List<ServiceCluster> serviceClusters() {
        return serviceClusters;
    }

    /** Returns an unmodifiable view of the mapping of config id to {@link ConfigProducer} */
    public Map<String, ConfigProducer> id2producer() {
        return Collections.unmodifiableMap(id2producer);
    }

    /**
     * @return this root's model repository
     */
    public ConfigModelRepo configModelRepo() {
        return configModelRepo;
    }

    @Override
    public DeployLogger deployLogger() {
        return getDeployState().getDeployLogger();
    }
    
}
