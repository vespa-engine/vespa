// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.provision.HostsXmlProvisioner;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Zone;
import com.yahoo.io.IOUtils;
import com.yahoo.schema.Application;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionBuilder;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.vespa.model.container.search.QueryProfilesBuilder;
import com.yahoo.vespa.model.container.search.SemanticRuleBuilder;
import com.yahoo.vespa.model.container.search.SemanticRules;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Contains various state during deploy that should be available in all builders of a {@link com.yahoo.config.model.ConfigModel}
 *
 * @author Ulf Lilleengen
 */
public class DeployState implements ConfigDefinitionStore {

    private final DeployLogger logger;
    private final FileRegistry fileRegistry;
    private final DocumentModel documentModel;
    private final List<Schema> schemas;
    private final ApplicationPackage applicationPackage;
    private final Optional<ConfigDefinitionRepo> configDefinitionRepo;
    private final Optional<Model> previousModel;
    private final boolean accessLoggingEnabledByDefault;
    private final ModelContext.Properties properties;
    private final Version vespaVersion;
    private final Set<ContainerEndpoint> endpoints;
    private final Zone zone;
    private final QueryProfiles queryProfiles;
    private final SemanticRules semanticRules;
    private final ImportedMlModels importedModels;
    private final ValidationOverrides validationOverrides;
    private final Version wantedNodeVespaVersion;
    private final Optional<DockerImage> wantedDockerImageRepo;
    private final Instant now;
    private final HostProvisioner provisioner;
    private final Provisioned provisioned;
    private final Reindexing reindexing;
    private final ExecutorService executor;

    public static DeployState createTestState() {
        return new Builder().build();
    }

    public static DeployState createTestState(DeployLogger testLogger) {
        return new Builder().deployLogger(testLogger).build();
    }

    public static DeployState createTestState(ApplicationPackage applicationPackage) {
        return new Builder().applicationPackage(applicationPackage).build();
    }

    private DeployState(Application application,
                        RankProfileRegistry rankProfileRegistry,
                        FileRegistry fileRegistry,
                        ExecutorService executor,
                        DeployLogger deployLogger,
                        Optional<HostProvisioner> hostProvisioner,
                        Provisioned provisioned,
                        ModelContext.Properties properties,
                        Version vespaVersion,
                        Optional<ConfigDefinitionRepo> configDefinitionRepo,
                        Optional<Model> previousModel,
                        Set<ContainerEndpoint> endpoints,
                        Collection<MlModelImporter> modelImporters,
                        Zone zone,
                        QueryProfiles queryProfiles,
                        SemanticRules semanticRules,
                        Instant now,
                        Version wantedNodeVespaVersion,
                        boolean accessLoggingEnabledByDefault,
                        Optional<DockerImage> wantedDockerImageRepo,
                        Reindexing reindexing,
                        Optional<ValidationOverrides> validationOverrides) {
        this.logger = deployLogger;
        this.fileRegistry = fileRegistry;
        this.executor = executor;
        this.rankProfileRegistry = rankProfileRegistry;
        this.applicationPackage = application.applicationPackage();
        this.properties = properties;
        this.vespaVersion = vespaVersion;
        this.previousModel = previousModel;
        this.accessLoggingEnabledByDefault = accessLoggingEnabledByDefault;
        this.provisioner = hostProvisioner.orElse(getDefaultModelHostProvisioner(applicationPackage));
        this.provisioned = provisioned;
        this.schemas = List.copyOf(application.schemas().values());
        this.documentModel = application.documentModel();
        this.configDefinitionRepo = configDefinitionRepo;
        this.endpoints = Set.copyOf(endpoints);
        this.zone = zone;
        this.queryProfiles = queryProfiles; // TODO: Remove this by seeing how pagetemplates are propagated
        this.semanticRules = semanticRules; // TODO: Remove this by seeing how pagetemplates are propagated
        this.importedModels = importMlModels(applicationPackage, modelImporters, executor);

        this.validationOverrides = validationOverrides.orElse(applicationPackage.getValidationOverrides().map(ValidationOverrides::fromXml)
                                                      .orElse(ValidationOverrides.empty));

        this.wantedNodeVespaVersion = wantedNodeVespaVersion;
        this.now = now;
        this.wantedDockerImageRepo = wantedDockerImageRepo;
        this.reindexing = reindexing;
    }

    public static HostProvisioner getDefaultModelHostProvisioner(ApplicationPackage applicationPackage) {
        try (Reader hostsReader = applicationPackage.getHosts()) {
            return hostsReader == null ? new SingleNodeProvisioner() : new HostsXmlProvisioner(hostsReader);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not read hosts.xml", e);
        }
    }

    public Provisioned provisioned() { return provisioned; }

    /** Get the global rank profile registry for this application. */
    public final RankProfileRegistry rankProfileRegistry() { return rankProfileRegistry; }

    /** Returns the validation overrides of this. This is never null */
    public ValidationOverrides validationOverrides() { return validationOverrides; }

    @Override
    public final Optional<ConfigDefinition> getConfigDefinition(ConfigDefinitionKey defKey) {
        if (existingConfigDefs == null) {
            existingConfigDefs = new LinkedHashMap<>();
            configDefinitionRepo.ifPresent(definitionRepo -> existingConfigDefs.putAll(createLazyMapping(definitionRepo)));
            existingConfigDefs.putAll(applicationPackage.getAllExistingConfigDefs());
        }
        if ( ! existingConfigDefs.containsKey(defKey)) return Optional.empty();

        if (defArchive.get(defKey) != null)
            return Optional.of(defArchive.get(defKey));

        ConfigDefinition def = existingConfigDefs.get(defKey).parse();

        defArchive.put(defKey, def);
        return Optional.of(def);
    }

    private static Map<ConfigDefinitionKey, UnparsedConfigDefinition> createLazyMapping(ConfigDefinitionRepo configDefinitionRepo) {
        Map<ConfigDefinitionKey, UnparsedConfigDefinition> keyToRepo = new LinkedHashMap<>();
        for (final Map.Entry<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> defEntry : configDefinitionRepo.getConfigDefinitions().entrySet()) {
            keyToRepo.put(defEntry.getKey(), new UnparsedConfigDefinition() {
                @Override
                public ConfigDefinition parse() {
                    return ConfigDefinitionBuilder.createConfigDefinition(configDefinitionRepo.getConfigDefinitions().get(defEntry.getKey()).getCNode());
                }

                @Override
                public String getUnparsedContent() {
                    throw new UnsupportedOperationException("Cannot get unparsed content from " + defEntry.getKey());
                }
            });
        }
        return keyToRepo;
    }

    private static ImportedMlModels importMlModels(ApplicationPackage applicationPackage,
                                                   Collection<MlModelImporter> modelImporters,
                                                   ExecutorService executor) {
        File importFrom = applicationPackage.getFileReference(ApplicationPackage.MODELS_DIR);
        ImportedMlModels importedModels = new ImportedMlModels(importFrom, executor, modelImporters);
        for (var entry : importedModels.getSkippedModels().entrySet())
            throw new IllegalArgumentException("Could not import model '" + entry.getKey() + "': " + entry.getValue());
        return importedModels;
    }

    // Global registry of rank profiles.
    // TODO: I think this can be removed when we remove "<search version=2.0>" and only support content.
    private final RankProfileRegistry rankProfileRegistry;

    // Mapping from key to something that can create a config definition.
    private Map<ConfigDefinitionKey, UnparsedConfigDefinition> existingConfigDefs = null;

    // Cache of config defs for all [def,version] combinations looked up so far.
    private final Map<ConfigDefinitionKey, ConfigDefinition> defArchive = new LinkedHashMap<>();

    public ApplicationPackage getApplicationPackage() {
        return applicationPackage;
    }

    public List<Schema> getSchemas() { return schemas; }

    public DocumentModel getDocumentModel() {
        return documentModel;
    }

    public DeployLogger getDeployLogger() {
        return logger;
    }

    public boolean getAccessLoggingEnabledByDefault() {
        return accessLoggingEnabledByDefault;
    }

    public FileRegistry getFileRegistry() {
        return fileRegistry;
    }

    public HostProvisioner getProvisioner() { return provisioner; }

    public ModelContext.Properties getProperties() { return properties; }

    public ModelContext.FeatureFlags featureFlags() { return properties.featureFlags(); }

    public Version getVespaVersion() { return vespaVersion; }

    public Optional<Model> getPreviousModel() { return previousModel; }

    public boolean isHosted() {
        return properties.hostedVespa();
    }

    public Set<ContainerEndpoint> getEndpoints() {
        return endpoints;
    }

    /** Returns the zone in which this is currently running */
    public Zone zone() { return zone; }

    public QueryProfiles getQueryProfiles() { return queryProfiles; }

    public SemanticRules getSemanticRules() { return semanticRules; }

    /** The (machine learned) models imported from the models/ directory, as an unmodifiable map indexed by model name */
    public ImportedMlModels getImportedModels() { return importedModels; }

    public ExecutorService getExecutor() { return executor; }

    public Version getWantedNodeVespaVersion() { return wantedNodeVespaVersion; }

    public Optional<DockerImage> getWantedDockerImageRepo() { return wantedDockerImageRepo; }

    public Instant now() { return now; }

    public Optional<EndpointCertificateSecrets> endpointCertificateSecrets() { return properties.endpointCertificateSecrets(); }

    public Optional<String> tlsClientAuthority() {
        var caFile = applicationPackage.getClientSecurityFile();
        if (caFile.exists()) {
            try {
                var caPem = IOUtils.readAll(caFile.createReader());
                return Optional.of(caPem);
            } catch (FileNotFoundException e) {
                return Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed reading certificate from application: " + caFile.getPath(), e);
            }
        } else {
            return Optional.empty();
        }
    }

    public Optional<Reindexing> reindexing() { return Optional.ofNullable(reindexing); }

    public boolean isHostedTenantApplication(ApplicationType type) {
        boolean isTesterApplication = getProperties().applicationId().instance().isTester();
        return isHosted() && type == ApplicationType.DEFAULT && !isTesterApplication;
    }

    public static class Builder {

        private ApplicationPackage applicationPackage = MockApplicationPackage.createEmpty();
        private FileRegistry fileRegistry = new MockFileRegistry();
        private ExecutorService executor = new InThreadExecutorService();
        private DeployLogger logger = new BaseDeployLogger();
        private Optional<HostProvisioner> hostProvisioner = Optional.empty();
        private Provisioned provisioned = new Provisioned();
        private ModelContext.Properties properties = new TestProperties();
        private Version version = new Version(1, 0, 0);
        private Optional<ConfigDefinitionRepo> configDefinitionRepo = Optional.empty();
        private Optional<Model> previousModel = Optional.empty();
        private Set<ContainerEndpoint> endpoints = Set.of();
        private Collection<MlModelImporter> modelImporters = Collections.emptyList();
        private Zone zone = Zone.defaultZone();
        private Instant now = Instant.now();
        private Version wantedNodeVespaVersion = Vtag.currentVersion;
        private boolean accessLoggingEnabledByDefault = true;
        private Optional<DockerImage> wantedDockerImageRepo = Optional.empty();
        private RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        private QueryProfiles queryProfiles = null;
        private Reindexing reindexing = null;
        private Optional<ValidationOverrides> validationOverrides = Optional.empty();

        public Builder() {}

        public Builder applicationPackage(ApplicationPackage applicationPackage) {
            this.applicationPackage = applicationPackage;
            return this;
        }

        public Builder fileRegistry(FileRegistry fileRegistry) {
            this.fileRegistry = fileRegistry;
            return this;
        }

        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder deployLogger(DeployLogger logger) {
            this.logger = logger;
            return this;
        }

        public Builder modelHostProvisioner(HostProvisioner modelProvisioner) {
            this.hostProvisioner = Optional.of(modelProvisioner);
            return this;
        }

        public Builder provisioned(Provisioned provisioned) {
            this.provisioned = provisioned;
            return this;
        }

        public Builder properties(ModelContext.Properties properties) {
            this.properties = properties;
            return this;
        }

        public Builder vespaVersion(Version version) {
            this.version = version;
            return this;
        }

        public Builder configDefinitionRepo(ConfigDefinitionRepo configDefinitionRepo) {
            this.configDefinitionRepo = Optional.of(configDefinitionRepo);
            return this;
        }

        public Builder previousModel(Model previousModel) {
            this.previousModel = Optional.of(previousModel);
            return this;
        }

        public Builder endpoints(Set<ContainerEndpoint> endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        public Builder modelImporters(Collection<MlModelImporter> modelImporters) {
            this.modelImporters = modelImporters;
            return this;
        }

        public Builder zone(Zone zone) {
            this.zone = zone;
            return this;
        }

        public Builder now(Instant now) {
            this.now = now;
            return this;
        }

        public Builder wantedNodeVespaVersion(Version version) {
            this.wantedNodeVespaVersion = version;
            return this;
        }

        public Builder wantedDockerImageRepo(Optional<DockerImage> dockerImageRepo) {
            this.wantedDockerImageRepo = dockerImageRepo;
            return this;
        }

        /**
         * Whether access logging is enabled for an application without an accesslog element in services.xml.
         * True by default.
         */
        public Builder accessLoggingEnabledByDefault(boolean accessLoggingEnabledByDefault) {
            this.accessLoggingEnabledByDefault = accessLoggingEnabledByDefault;
            return this;
        }

        public Builder rankProfileRegistry(RankProfileRegistry rankProfileRegistry) {
            this.rankProfileRegistry = rankProfileRegistry;
            return this;
        }

        public Builder queryProfiles(QueryProfiles queryProfiles) {
            this.queryProfiles = queryProfiles;
            return this;
        }

        public Builder queryProfiles(QueryProfileRegistry queryProfileRegistry) {
            this.queryProfiles = new QueryProfiles(queryProfileRegistry, logger);
            return this;
        }

        public Builder reindexing(Reindexing reindexing) {
            this.reindexing = Objects.requireNonNull(reindexing);
            return this;
        }

        public Builder validationOverrides(ValidationOverrides validationOverrides) {
            this.validationOverrides = Optional.of(validationOverrides);
            return this;
        }

        public DeployState build() {
            return build(new ValidationParameters());
        }

        public DeployState build(ValidationParameters validationParameters) {
            if (queryProfiles == null)
                queryProfiles = new QueryProfilesBuilder().build(applicationPackage, logger);
            SemanticRules semanticRules = new SemanticRuleBuilder().build(applicationPackage);
            Application application = new ApplicationBuilder(applicationPackage, fileRegistry, logger, properties,
                                                             rankProfileRegistry, queryProfiles.getRegistry())
                    .build(! validationParameters.ignoreValidationErrors());
            return new DeployState(application,
                                   rankProfileRegistry,
                                   fileRegistry,
                                   executor,
                                   logger,
                                   hostProvisioner,
                                   provisioned,
                                   properties,
                                   version,
                                   configDefinitionRepo,
                                   previousModel,
                                   endpoints,
                                   modelImporters,
                                   zone,
                                   queryProfiles,
                                   semanticRules,
                                   now,
                                   wantedNodeVespaVersion,
                                   accessLoggingEnabledByDefault,
                                   wantedDockerImageRepo,
                                   reindexing,
                                   validationOverrides);
        }

    }

}

