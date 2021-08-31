// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigInstance.Builder;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.QrConfig;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.LargeRankExpressions;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.OnnxModels;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.RankingConstants;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RankProfileList;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.processing.Processing;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.config.GenericConfig.GenericConfigBuilder;
import com.yahoo.vespa.model.InstanceResolver.PackagePrefix;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.builder.VespaModelBuilder;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.clients.Clients;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileReferencesRepository;
import com.yahoo.vespa.model.generic.service.ServiceCluster;
import com.yahoo.vespa.model.ml.ConvertedModel;
import com.yahoo.vespa.model.ml.ModelName;
import com.yahoo.vespa.model.ml.OnnxModelInfo;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.utils.internal.ReflectionUtil;
import org.xml.sax.SAXException;

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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.config.codegen.ConfiggenUtil.createClassName;
import static com.yahoo.text.StringUtilities.quote;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * <p>
 * The root ConfigProducer node for all Vespa systems (there is currently only one).
 * The main class for building the Vespa model.
 * <p>
 * The vespa model starts in an unfrozen state, where children can be added freely,
 * but no structure dependent information can be used.
 * When frozen, structure dependent information (such as config id) are
 * made available, but no additional config producers can be added.
 *
 * @author gjoranv
 */
public final class VespaModel extends AbstractConfigProducerRoot implements Serializable, Model {

    private static final long serialVersionUID = 1L;

    public static final Logger log = Logger.getLogger(VespaModel.class.getName());

    private final Version version;
    private final ConfigModelRepo configModelRepo = new ConfigModelRepo();
    private final AllocatedHosts allocatedHosts;

    /** The config id for the root config producer */
    public static final String ROOT_CONFIGID = "";

    private final ApplicationConfigProducerRoot root;

    private final ApplicationPackage applicationPackage;

    /** Generic service instances - service clusters which have no specific model */
    private final List<ServiceCluster> serviceClusters = new ArrayList<>();

    /** The global rank profiles of this model */
    private final RankProfileList rankProfileList;

    /** The global ranking constants of this model */
    private final RankingConstants rankingConstants;

    /** Large rank expression files of this */
    private final LargeRankExpressions largeRankExpressions;

    private final FileRegistry fileRegistry;

    /** The validation overrides of this. This is never null. */
    private final ValidationOverrides validationOverrides;

    private final FileReferencesRepository fileReferencesRepository = new FileReferencesRepository();

    private final Provisioned provisioned;

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
        this(configModelRegistry, new DeployState.Builder().applicationPackage(app).build());
    }

    /**
     * Constructs vespa model using config given in app
     *
     * @param configModelRegistry a registry of config model "main" classes which may be used
     *        to instantiate config models
     * @param deployState the global deploy state to use for this model.
     */
    public VespaModel(ConfigModelRegistry configModelRegistry, DeployState deployState) throws IOException, SAXException {
        this(configModelRegistry, deployState, true);
    }

    private VespaModel(ConfigModelRegistry configModelRegistry, DeployState deployState, boolean complete)
            throws IOException, SAXException {
        super("vespamodel");
        version = deployState.getVespaVersion();
        fileRegistry = deployState.getFileRegistry();
        largeRankExpressions = new LargeRankExpressions(deployState.getFileRegistry());
        rankingConstants = new RankingConstants(deployState.getFileRegistry());
        validationOverrides = deployState.validationOverrides();
        applicationPackage = deployState.getApplicationPackage();
        provisioned = deployState.provisioned();
        VespaModelBuilder builder = new VespaDomBuilder();
        root = builder.getRoot(VespaModel.ROOT_CONFIGID, deployState, this);

        createGlobalRankProfiles(deployState.getDeployLogger(), deployState.getImportedModels(),
                                 deployState.rankProfileRegistry(), deployState.getQueryProfiles());
        rankProfileList = new RankProfileList(null, // null search -> global
                                              rankingConstants,
                                              largeRankExpressions,
                                              new OnnxModels(deployState.getFileRegistry()),
                                              AttributeFields.empty,
                                              deployState.rankProfileRegistry(),
                                              deployState.getQueryProfiles().getRegistry(),
                                              deployState.getImportedModels(),
                                              deployState.getProperties());

        HostSystem hostSystem = root.hostSystem();
        if (complete) { // create a completed, frozen model
            configModelRepo.readConfigModels(deployState, this, builder, root, new VespaConfigModelRegistry(configModelRegistry));
            addServiceClusters(deployState, builder);
            setupRouting(deployState);
            getAdmin().addPerHostServices(hostSystem.getHosts(), deployState);
            freezeModelTopology();
            root.prepare(configModelRepo);
            configModelRepo.prepareConfigModels(deployState);
            validateWrapExceptions();
            hostSystem.dumpPortAllocations();
            propagateRestartOnDeploy();
        }
        // else: create a model with no services instantiated (no-op)

        // must be done last
        this.allocatedHosts = AllocatedHosts.withHosts(hostSystem.getHostSpecs());
    }

    @Override
    public Map<String, Set<String>> documentTypesByCluster() {
        return getContentClusters().entrySet().stream()
                                   .collect(toMap(cluster -> cluster.getKey(),
                                             cluster -> cluster.getValue().getDocumentDefinitions().keySet()));
    }

    @Override
    public Map<String, Set<String>> indexedDocumentTypesByCluster() {
        return getContentClusters().entrySet().stream()
                                   .collect(toUnmodifiableMap(cluster -> cluster.getKey(),
                                                         cluster -> documentTypesWithIndex(cluster.getValue())));
    }

    private static Set<String> documentTypesWithIndex(ContentCluster content) {
        Set<String> typesWithIndexMode = content.getSearch().getDocumentTypesWithIndexedCluster().stream()
                                                .map(type -> type.getFullName().getName())
                                                .collect(toSet());

        Set<String> typesWithIndexedFields = content.getSearch().getIndexed() == null
                                             ? Set.of()
                                             : content.getSearch().getIndexed().getDocumentDbs().stream()
                                                      .filter(database -> database.getDerivedConfiguration()
                                                                                  .getSearch()
                                                                                  .allConcreteFields()
                                                                                  .stream().anyMatch(SDField::doesIndexing))
                                                      .map(database -> database.getInputDocType())
                                                      .collect(toSet());

        return typesWithIndexMode.stream().filter(typesWithIndexedFields::contains).collect(toUnmodifiableSet());
    }

    private void propagateRestartOnDeploy() {
        if (applicationPackage.getMetaData().isInternalRedeploy()) return;

        // Propagate application config setting of restartOnDeploy to cluster deferChangesUntilRestart
        for (ApplicationContainerCluster containerCluster : getContainerClusters().values()) {
            QrConfig config = getConfig(QrConfig.class, containerCluster.getConfigId());
            if (config.restartOnDeploy())
                containerCluster.setDeferChangesUntilRestart(true);
        }
    }

    /** Returns the application package owning this */
    public ApplicationPackage applicationPackage() { return applicationPackage; }

    /** Returns the global ranking constants of this */
    public RankingConstants rankingConstants() { return rankingConstants; }

    public LargeRankExpressions rankExpressionFiles() { return largeRankExpressions; }

    /** Creates a mutable model with no services instantiated */
    public static VespaModel createIncomplete(DeployState deployState) throws IOException, SAXException {
        return new VespaModel(new NullConfigModelRegistry(), deployState, false);
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
    private void addServiceClusters(DeployState deployState, VespaModelBuilder builder) {
        serviceClusters.addAll(builder.getClusters(deployState, this));
    }

    /**
     * Creates a rank profile not attached to any search definition, for each imported model in the application package,
     * and adds it to the given rank profile registry.
     */
    private void createGlobalRankProfiles(DeployLogger deployLogger, ImportedMlModels importedModels,
                                          RankProfileRegistry rankProfileRegistry,
                                          QueryProfiles queryProfiles) {
        if ( ! importedModels.all().isEmpty()) { // models/ directory is available
            for (ImportedMlModel model : importedModels.all()) {
                // Due to automatic naming not guaranteeing unique names, there must be a 1-1 between OnnxModels and global RankProfiles.
                OnnxModels onnxModels = onnxModelInfoFromSource(model);
                RankProfile profile = new RankProfile(model.name(), this, rankProfileRegistry, onnxModels);
                rankProfileRegistry.add(profile);
                ConvertedModel convertedModel = ConvertedModel.fromSource(new ModelName(model.name()),
                                                                          model.name(), profile, queryProfiles.getRegistry(), model);
                convertedModel.expressions().values().forEach(f -> profile.addFunction(f, false));
            }
        }
        else { // generated and stored model information may be available instead
            ApplicationFile generatedModelsDir = applicationPackage.getFile(ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR);
            for (ApplicationFile generatedModelDir : generatedModelsDir.listFiles()) {
                String modelName = generatedModelDir.getPath().last();
                if (modelName.contains(".")) continue; // Name space: Not a global profile
                // Due to automatic naming not guaranteeing unique names, there must be a 1-1 between OnnxModels and global RankProfiles.
                OnnxModels onnxModels = onnxModelInfoFromStore(modelName);
                RankProfile profile = new RankProfile(modelName, this, rankProfileRegistry, onnxModels);
                rankProfileRegistry.add(profile);
                ConvertedModel convertedModel = ConvertedModel.fromStore(new ModelName(modelName), modelName, profile);
                convertedModel.expressions().values().forEach(f -> profile.addFunction(f, false));
            }
        }
        new Processing().processRankProfiles(deployLogger, rankProfileRegistry, queryProfiles, true, false);
    }

    private OnnxModels onnxModelInfoFromSource(ImportedMlModel model) {
        OnnxModels onnxModels = new OnnxModels(fileRegistry);
        if (model.modelType().equals(ImportedMlModel.ModelType.ONNX)) {
            String path = model.source();
            String applicationPath = this.applicationPackage.getFileReference(Path.fromString("")).toString();
            if (path.startsWith(applicationPath)) {
                path = path.substring(applicationPath.length() + 1);
            }
            loadOnnxModelInfo(onnxModels, model.name(), path);
        }
        return onnxModels;
    }

    private OnnxModels onnxModelInfoFromStore(String modelName) {
        String path = ApplicationPackage.MODELS_DIR.append(modelName + ".onnx").toString();
        OnnxModels onnxModels = new OnnxModels(fileRegistry);
        loadOnnxModelInfo(onnxModels, modelName, path);
        return onnxModels;
    }

    private void loadOnnxModelInfo(OnnxModels onnxModels, String name, String path) {
        boolean modelExists = OnnxModelInfo.modelExists(path, this.applicationPackage);
        if ( ! modelExists) {
            path = ApplicationPackage.MODELS_DIR.append(path).toString();
            modelExists = OnnxModelInfo.modelExists(path, this.applicationPackage);
        }
        if (modelExists) {
            OnnxModelInfo onnxModelInfo = OnnxModelInfo.load(path, this.applicationPackage);
            if (onnxModelInfo.getModelPath() != null) {
                OnnxModel onnxModel = new OnnxModel(name, onnxModelInfo.getModelPath());
                onnxModel.setModelInfo(onnxModelInfo);
                onnxModels.add(onnxModel);
            }
        }
    }

    /** Returns the global rank profiles as a rank profile list */
    public RankProfileList rankProfileList() { return rankProfileList; }

    private void setupRouting(DeployState deployState) {
        root.setupRouting(deployState, this, configModelRepo);
    }

    /** Returns the one and only HostSystem of this VespaModel */
    public HostSystem hostSystem() {
        return root.hostSystem();
    }

    /** Return a collection of all hostnames used in this application */
    @Override
    public Set<HostInfo> getHosts() {
        return hostSystem().getHosts().stream()
                           .map(HostResource::getHostInfo)
                           .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public FileReferencesRepository fileReferencesRepository() { return fileReferencesRepository; }

    public Set<FileReference> fileReferences() { return fileReferencesRepository.allFileReferences(); }

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

    @Override
    public Version version() {
        return version;
    }

    /**
     * Resolves config of the given type and config id, by first instantiating the correct {@link com.yahoo.config.ConfigInstance.Builder},
     * calling {@link #getConfig(com.yahoo.config.ConfigInstance.Builder, String)}. The default values used will be those of the config
     * types in the model.
     *
     * @param clazz the type of config
     * @param configId the config id
     * @return a config instance of the given type
     */
    @Override
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
        Class<?> builderClazz = configClass.getClassLoader().loadClass(configClass.getName() + "$Builder");
        return (Builder)builderClazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Throw if the config id does not exist in the model.
     *
     * @param configId a config id
     */
    protected void checkId(String configId) {
        if ( ! id2producer.containsKey(configId)) {
            log.log(Level.FINE, () -> "Invalid config id: " + configId);
        }
    }

    /**
     * Resolves config for a given config id and populates the given builder with the config.
     *
     * @param builder a configinstance builder
     * @param configId the config id for the config client
     * @return the builder if a producer was found, and it did apply config, null otherwise
     */
    @Override
    public ConfigInstance.Builder getConfig(ConfigInstance.Builder builder, String configId) {
        checkId(configId);
        Optional<ConfigProducer> configProducer = getConfigProducer(configId);
        if (configProducer.isEmpty()) return null;

        populateConfigBuilder(builder, configProducer.get());
        return builder;
    }

    private static void populateConfigBuilder(Builder builder, ConfigProducer configProducer) {
        boolean found = configProducer.cascadeConfig(builder);
        boolean foundOverride = configProducer.addUserConfig(builder);
        log.log(Level.FINE, () -> "Trying to get config for " + builder.getClass().getDeclaringClass().getName() +
                " for config id " + quote(configProducer.getConfigId()) +
                ", found=" + found + ", foundOverride=" + foundOverride);
    }

    /**
     * Resolve config for a given key and config definition
     *
     * @param configKey the key to resolve.
     * @param targetDef the config definition to use for the schema
     * @return the resolved config instance
     */
    @Override
    public ConfigInstance.Builder getConfigInstance(ConfigKey<?> configKey, com.yahoo.vespa.config.buildergen.ConfigDefinition targetDef) {
        Objects.requireNonNull(targetDef, "config definition cannot be null");
        return resolveToBuilder(configKey);
    }

    /**
     * Resolves the given config key into a correctly typed ConfigBuilder
     * and fills in the config from this model.
     *
     * @return A new config builder with config from this model filled in,.
     */
    private ConfigInstance.Builder resolveToBuilder(ConfigKey<?> key) {
        ConfigInstance.Builder builder = createBuilder(new ConfigDefinitionKey(key));
        getConfig(builder, key.getConfigId());
        return builder;
    }

    @Override
    public Set<ConfigKey<?>> allConfigsProduced() {
        Set<ConfigKey<?>> keySet = new LinkedHashSet<>();
        for (ConfigProducer producer : id2producer().values()) {
            keySet.addAll(configsProduced(producer));
        }
        return keySet;
    }

    private ConfigInstance.Builder createBuilder(ConfigDefinitionKey key) {
        String className = createClassName(key.getName());
        Class<?> clazz;

        Pair<String, ClassLoader> fullClassNameAndLoader = getClassLoaderForProducer(key, className);
        String fullClassName = fullClassNameAndLoader.getFirst();
        ClassLoader classLoader = fullClassNameAndLoader.getSecond();

        final String builderName = fullClassName + "$Builder";
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
            log.log(Level.FINE, () -> "No producer found to get classloader from for " + fullClassName + ". Using default");
        }
        try {
            clazz = classLoader.loadClass(builderName);
        } catch (ClassNotFoundException e) {
            log.log(Level.FINE, () -> "Tried to load " + builderName + ", not found, trying with generic builder");
            // TODO: Enable config compiler when configserver is using new API.
            // ConfigCompiler compiler = new LazyConfigCompiler(Files.createTempDir());
            // return compiler.compile(targetDef.generateClass()).newInstance();
            return new GenericConfigBuilder(key, new ConfigPayloadBuilder());
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

    /**
     * Takes a candidate class name and tries to find a config producer for that class in the model. First, an
     * attempt is made with the given full class name, and if unsuccessful, then with 'com.yahoo.' prefixed to it.
     * Returns the full class name and the class loader that a producer was found for. If no producer was found,
     * returns the full class name with prefix, and null for the class loader.
     */
    private Pair<String, ClassLoader> getClassLoaderForProducer(ConfigDefinitionKey key, String shortClassName) {
        String fullClassNameWithComYahoo = InstanceResolver.packageName(key, PackagePrefix.COM_YAHOO) + "." + shortClassName;
        String fullClassNameWithoutPrefix = InstanceResolver.packageName(key, PackagePrefix.NONE) + "." + shortClassName;
        String producerSuffix = "$Producer";

        ClassLoader loader = getConfigClassLoader(fullClassNameWithoutPrefix + producerSuffix);
        if (loader != null) return new Pair<>(fullClassNameWithoutPrefix, loader);

        return new Pair<>(fullClassNameWithComYahoo,
                          getConfigClassLoader(fullClassNameWithComYahoo + producerSuffix));
    }

    /**
     * The set of all config ids present
     * @return set of config ids
     */
    public Set<String> allConfigIds() {
        return id2producer.keySet();
    }

    @Override
    public AllocatedHosts allocatedHosts() {
        return allocatedHosts;
    }

    private static Set<ConfigKey<?>> configsProduced(ConfigProducer cp) {
        Set<ConfigKey<?>> ret = ReflectionUtil.getAllConfigsProduced(cp.getClass(), cp.getConfigId());
        UserConfigRepo userConfigs = cp.getUserConfigs();
        for (ConfigDefinitionKey userKey : userConfigs.configsProduced()) {
            ret.add(new ConfigKey<>(userKey.getName(), cp.getConfigId(), userKey.getNamespace()));
        }
        return ret;
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
    public Map<String, ApplicationContainerCluster> getContainerClusters() {
        Map<String, ApplicationContainerCluster> clusters = new LinkedHashMap<>();
        for (ContainerModel model : configModelRepo.getModels(ContainerModel.class)) {
            if (model.getCluster() instanceof ApplicationContainerCluster) {
                clusters.put(model.getId(), (ApplicationContainerCluster) model.getCluster());
            }
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

    /** Returns this root's model repository */
    public ConfigModelRepo configModelRepo() {
        return configModelRepo;
    }

    /** If provisioning through the node repo, returns the provision requests issued during build of this */
    public Provisioned provisioned() { return provisioned; }

    /** Returns the id of all clusters in this */
    public Set<ClusterSpec.Id> allClusters() {
        return hostSystem().getHosts().stream()
                                      .map(HostResource::spec)
                                      .filter(spec -> spec.membership().isPresent())
                                      .map(spec -> spec.membership().get().cluster().id())
                                      .collect(Collectors.toSet());
    }

}
