// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.application.api.UnparsedConfigDefinition;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.provision.HostsXmlProvisioner;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Zone;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionBuilder;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.vespa.model.container.search.QueryProfilesBuilder;
import com.yahoo.vespa.model.container.search.SemanticRuleBuilder;
import com.yahoo.vespa.model.container.search.SemanticRules;
import com.yahoo.vespa.model.search.SearchDefinition;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Contains various state during deploy that should be available in all builders of a {@link com.yahoo.config.model.ConfigModel}
 *
 * @author Ulf Lilleengen
 */
public class DeployState implements ConfigDefinitionStore {

    private final DeployLogger logger;
    private final FileRegistry fileRegistry;
    private final DocumentModel documentModel;
    private final List<SearchDefinition> searchDefinitions;
    private final ApplicationPackage applicationPackage;
    private final Optional<ConfigDefinitionRepo> configDefinitionRepo;
    private final Optional<ApplicationPackage> permanentApplicationPackage;
    private final Optional<Model> previousModel;
    private final DeployProperties properties;
    private final Set<Rotation> rotations;
    private final Zone zone;
    private final QueryProfiles queryProfiles;
    private final SemanticRules semanticRules;
    private final ValidationOverrides validationOverrides;
    private final Version wantedNodeVespaVersion;
    private final Instant now;
    private final HostProvisioner provisioner;
    private final boolean disableFiledistributor;

    public static DeployState createTestState() {
        return new Builder().build();
    }

    public static DeployState createTestState(ApplicationPackage applicationPackage) {
        return new Builder().applicationPackage(applicationPackage).build();
    }

    private DeployState(ApplicationPackage applicationPackage, SearchDocumentModel searchDocumentModel, RankProfileRegistry rankProfileRegistry,
                        FileRegistry fileRegistry, DeployLogger deployLogger, Optional<HostProvisioner> hostProvisioner, DeployProperties properties,
                        Optional<ApplicationPackage> permanentApplicationPackage, Optional<ConfigDefinitionRepo> configDefinitionRepo,
                        java.util.Optional<Model> previousModel, Set<Rotation> rotations, Zone zone, QueryProfiles queryProfiles,
                        SemanticRules semanticRules, Instant now, Version wantedNodeVespaVersion, boolean disableFiledistributor) {
        this.logger = deployLogger;
        this.fileRegistry = fileRegistry;
        this.rankProfileRegistry = rankProfileRegistry;
        this.applicationPackage = applicationPackage;
        this.properties = properties;
        this.previousModel = previousModel;
        this.provisioner = hostProvisioner.orElse(getDefaultModelHostProvisioner(applicationPackage));
        this.searchDefinitions = searchDocumentModel.getSearchDefinitions();
        this.documentModel = searchDocumentModel.getDocumentModel();
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.configDefinitionRepo = configDefinitionRepo;
        this.rotations = rotations;
        this.zone = zone;
        this.queryProfiles = queryProfiles; // TODO: Remove this by seeing how pagetemplates are propagated
        this.semanticRules = semanticRules; // TODO: Remove this by seeing how pagetemplates are propagated
        this.validationOverrides = applicationPackage.getValidationOverrides().map(ValidationOverrides::fromXml).orElse(ValidationOverrides.empty);
        this.wantedNodeVespaVersion = wantedNodeVespaVersion;
        this.now = now;
        this.disableFiledistributor = disableFiledistributor;
    }

    public static HostProvisioner getDefaultModelHostProvisioner(ApplicationPackage applicationPackage) {
        if (applicationPackage.getHosts() == null) {
            return new SingleNodeProvisioner();
        } else {
            return new HostsXmlProvisioner(applicationPackage.getHosts());
        }
    }

    /** Get the global rank profile registry for this application. */
    public final RankProfileRegistry rankProfileRegistry() { return rankProfileRegistry; }

    /** Returns the validation overrides of this. This is never null */
    public ValidationOverrides validationOverrides() { return validationOverrides; }

    @Override
    public final Optional<ConfigDefinition> getConfigDefinition(ConfigDefinitionKey defKey) {
        if (existingConfigDefs == null) {
            existingConfigDefs = new LinkedHashMap<>();
            if (configDefinitionRepo.isPresent()) {
                existingConfigDefs.putAll(createLazyMapping(configDefinitionRepo.get()));
            }
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

    public List<SearchDefinition> getSearchDefinitions() {
        return searchDefinitions;
    }

    public DocumentModel getDocumentModel() {
        return documentModel;
    }

    public DeployLogger getDeployLogger() {
        return logger;
    }

    public FileRegistry getFileRegistry() {
        return fileRegistry;
    }

    public HostProvisioner getProvisioner() { return provisioner; }

    public Optional<ApplicationPackage> getPermanentApplicationPackage() {
        return permanentApplicationPackage;
    }

    public DeployProperties getProperties() { return properties; }

    public Optional<Model> getPreviousModel() { return previousModel; }

    public boolean isHosted() {
        return properties.hostedVespa();
    }

    public Set<Rotation> getRotations() {
        return this.rotations; // todo: consider returning a copy or immutable view
    }

    /** Returns the zone in which this is currently running */
    public Zone zone() { return zone; }

    public QueryProfiles getQueryProfiles() { return queryProfiles; }

    public SemanticRules getSemanticRules() { return semanticRules; }

    public Version getWantedNodeVespaVersion() { return wantedNodeVespaVersion; }

    public Instant now() { return now; }

    public boolean disableFiledistributor() { return disableFiledistributor; }

    public static class Builder {

        private ApplicationPackage applicationPackage = MockApplicationPackage.createEmpty();
        private FileRegistry fileRegistry = new MockFileRegistry();
        private DeployLogger logger = new BaseDeployLogger();
        private Optional<HostProvisioner> hostProvisioner = Optional.empty();
        private Optional<ApplicationPackage> permanentApplicationPackage = Optional.empty();
        private DeployProperties properties = new DeployProperties.Builder().build();
        private Optional<ConfigDefinitionRepo> configDefinitionRepo = Optional.empty();
        private Optional<Model> previousModel = Optional.empty();
        private Set<Rotation> rotations = new HashSet<>();
        private Zone zone = Zone.defaultZone();
        private Instant now = Instant.now();
        private Version wantedNodeVespaVersion = Vtag.currentVersion;
        private boolean disableFiledistributor = false;

        public Builder applicationPackage(ApplicationPackage applicationPackage) {
            this.applicationPackage = applicationPackage;
            return this;
        }

        public Builder fileRegistry(FileRegistry fileRegistry) {
            this.fileRegistry = fileRegistry;
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

        public Builder permanentApplicationPackage(Optional<ApplicationPackage> permanentApplicationPackage) {
            this.permanentApplicationPackage = permanentApplicationPackage;
            return this;
        }

        public Builder properties(DeployProperties properties) {
            this.properties = properties;
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

        public Builder rotations(Set<Rotation> rotations) {
            this.rotations = rotations;
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

        public Builder disableFiledistributor(boolean disableFiledistributor) {
            this.disableFiledistributor = disableFiledistributor;
            return this;
        }

        public DeployState build() {
            RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
            QueryProfiles queryProfiles = new QueryProfilesBuilder().build(applicationPackage);
            SemanticRules semanticRules = new SemanticRuleBuilder().build(applicationPackage);
            SearchDocumentModel searchDocumentModel = createSearchDocumentModel(rankProfileRegistry, logger, queryProfiles);
            return new DeployState(applicationPackage, searchDocumentModel, rankProfileRegistry, fileRegistry, logger, hostProvisioner,
                                   properties, permanentApplicationPackage, configDefinitionRepo, previousModel, rotations,
                                   zone, queryProfiles, semanticRules, now, wantedNodeVespaVersion, disableFiledistributor);
        }

        private SearchDocumentModel createSearchDocumentModel(RankProfileRegistry rankProfileRegistry, DeployLogger logger, QueryProfiles queryProfiles) {
            Collection<NamedReader> readers = applicationPackage.getSearchDefinitions();
            Map<String, String> names = new LinkedHashMap<>();
            SearchBuilder builder = new SearchBuilder(applicationPackage, rankProfileRegistry);
            for (NamedReader reader : readers) {
                try {
                    String readerName = reader.getName();
                    String searchName = builder.importReader(reader, readerName, logger);
                    String sdName = stripSuffix(readerName, ApplicationPackage.SD_NAME_SUFFIX);
                    names.put(searchName, sdName);
                    if (!sdName.equals(searchName)) {
                        throw new IllegalArgumentException("Search definition file name ('" + sdName + "') and name of " +
                                                           "search element ('" + searchName +
                                                           "') are not equal for file '" + readerName + "'");
                    }
                } catch (ParseException e) {
                    throw new IllegalArgumentException("Could not parse search definition file '" + getSearchDefinitionRelativePath(reader.getName()) + "': " + e.getMessage(), e);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Could not read search definition file '" + getSearchDefinitionRelativePath(reader.getName()) + "': " + e.getMessage(), e);
                } finally {
                    closeIgnoreException(reader.getReader());
                }
            }
            builder.build(logger, queryProfiles);
            return SearchDocumentModel.fromBuilderAndNames(builder, names);
        }

        private String getSearchDefinitionRelativePath(String name) {
            return ApplicationPackage.SEARCH_DEFINITIONS_DIR + File.separator + name;
        }

        private static String stripSuffix(String nodeName, String postfix) {
            assert (nodeName.endsWith(postfix));
            return nodeName.substring(0, nodeName.length() - postfix.length());
        }

        @SuppressWarnings("EmptyCatchBlock")
        private static void closeIgnoreException(Reader reader) {
            try {
                reader.close();
            } catch(Exception e) {}
        }
    }

}

